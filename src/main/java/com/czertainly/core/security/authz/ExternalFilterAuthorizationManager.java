package com.czertainly.core.security.authz;

import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.AnonymousPrincipal;
import com.czertainly.core.security.authz.opa.dto.OpaRequestDetails;
import com.czertainly.core.security.authz.opa.dto.OpaRequestedResource;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.czertainly.core.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ExternalFilterAuthorizationManager extends AbstractExternalAuthorizationManager<RequestAuthorizationContext> {

    protected final Log log = LogFactory.getLog(this.getClass());

    private final OpaClient opaClient;

    private final ObjectMapper om;

    @Setter
    private RequestMatcher toAuthorizeRequestsMatcher;

    @Setter
    private RequestMatcher doNotAuthorizeAnonymousRequestsMatcher;

    private final AuditLogService auditLogService;

    public ExternalFilterAuthorizationManager(@Autowired OpaClient opaClient, @Autowired ObjectMapper om, @Autowired AuditLogService auditLogService) {
        this.opaClient = opaClient;
        this.om = om;
        this.auditLogService = auditLogService;
    }


    @Override
    protected AuthorizationDecision voteInternal(CzertainlyAuthenticationToken authentication, RequestAuthorizationContext requestAuthorizationContext) {
        OpaRequestDetails opaDetails = null;
        if (authentication.getDetails() instanceof WebAuthenticationDetails webAuthenticationDetails) {
            String remoteAddress = webAuthenticationDetails.getRemoteAddress();
            opaDetails = new OpaRequestDetails(remoteAddress);
        }
        return this.vote(authentication.getPrincipal().getRawData(), requestAuthorizationContext, opaDetails);
    }

    @Override
    protected AuthorizationDecision voteInternal(AnonymousAuthenticationToken authentication, RequestAuthorizationContext requestAuthorizationContext) {
        OpaRequestDetails opaDetails = null;
        if (authentication.getDetails() instanceof WebAuthenticationDetails webAuthenticationDetails) {
            String remoteAddress = webAuthenticationDetails.getRemoteAddress();
            opaDetails = new OpaRequestDetails(remoteAddress);
        }
        try {
            return this.vote(om.writeValueAsString(new AnonymousPrincipal(authentication.getName())), requestAuthorizationContext, opaDetails);
        } catch (JsonProcessingException e) {
            log.error("An error occurred during voting. Access will be denied.", e);
            return new AuthorizationDecision(false);
        }
    }

    private AuthorizationDecision vote(String principal, RequestAuthorizationContext requestAuthorizationContext, OpaRequestDetails details) {
        String requestUrl = requestAuthorizationContext.getRequest().getRequestURL().toString();
        try {
            List<String> urlParts =
                    Arrays.stream(requestUrl
                            .split("/"))
                    .filter(p -> !p.isBlank())
                    .toList();

            OpaRequestedResource resource = new OpaRequestedResource(urlParts);


            OpaResourceAccessResult result = this.checkAccess(principal, resource, details);
            if (result.isAuthorized()) {
                log.trace(
                        String.format(
                                "Access to the endpoint '%s' has been granted by the following rules [%s].",
                                requestUrl,
                                String.join(",", result.getAllow())
                        )
                );
                return new AuthorizationDecision(true);
            } else {
                String message = "Access to the endpoint '%s' has been denied.".formatted(requestUrl);
                auditLogService.logAuthentication(Operation.AUTHENTICATION, OperationResult.FAILURE, message, null);
                return new AuthorizationDecision(false);
            }
        } catch (Exception e) {
            String message = "Unable verify access to the endpoint '%s'. Voting to deny access.".formatted(requestUrl);
            auditLogService.logAuthentication(Operation.AUTHENTICATION, OperationResult.FAILURE, message, null);
            log.error(message, e);
            return new AuthorizationDecision(false);
        }
    }

    @Override
    protected boolean canDecide(Authentication auth, RequestAuthorizationContext requestAuthorizationContext) {
        if (auth instanceof AnonymousAuthenticationToken) {
            // First to verify that the request was not explicitly excluded from the voting
            boolean doNotAuthorize = doNotAuthorizeAnonymousRequestsMatcher != null && this.doNotAuthorizeAnonymousRequestsMatcher.matches(requestAuthorizationContext.getRequest());
            if (doNotAuthorize) {
                log.trace("Voter is set not to decide for the " + requestAuthorizationContext);
                return false;
            }

            // For security reasons, we want to authorize all anonymous requests except those that were excluded in step one
            log.trace("Will vote because the request is authenticated as anonymous.");
            return true;
        }

        // Finally, check if non-anonymous request should be authorized
        boolean shouldAuthorize = toAuthorizeRequestsMatcher == null || this.toAuthorizeRequestsMatcher.matches(requestAuthorizationContext.getRequest());
        if (!shouldAuthorize) {
            log.trace("Voter can't decide for the " + requestAuthorizationContext);
        }

        return shouldAuthorize;
    }

    protected OpaResourceAccessResult checkAccess(String principal, OpaRequestedResource resource, OpaRequestDetails opaDetails) {
        try {
            return this.opaClient.checkResourceAccess(OpaPolicy.ENDPOINT.policyName, resource, principal, opaDetails);
        } catch (Exception e) {
            log.error(
                    
                            "An error occurred during the authorization request to the OPA policy '%s'.".formatted(
                            OpaPolicy.ENDPOINT.policyName),
                    e
            );
            return OpaResourceAccessResult.unauthorized();
        }
    }

}

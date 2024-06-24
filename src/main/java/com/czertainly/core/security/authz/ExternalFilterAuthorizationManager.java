package com.czertainly.core.security.authz;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.AnonymousPrincipal;
import com.czertainly.core.security.authz.opa.dto.OpaRequestDetails;
import com.czertainly.core.security.authz.opa.dto.OpaRequestedResource;
import com.czertainly.core.security.authz.opa.dto.OpaResourceAccessResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class ExternalFilterAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    protected final Log logger = LogFactory.getLog(this.getClass());

    private final OpaClient opaClient;
    private final ObjectMapper om;
    private RequestMatcher toAuthorizeRequestsMatcher;
    private RequestMatcher doNotAuthorizeAnonymousRequestsMatcher;

    @Autowired
    public ExternalFilterAuthorizationManager(OpaClient opaClient, ObjectMapper om) {
        this.opaClient = opaClient;
        this.om = om;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier, RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        HttpServletRequest request = context.getRequest();

        OpaRequestDetails opaDetails = null;
        if (authentication.getDetails() instanceof WebAuthenticationDetails) {
            String remoteAddress = ((WebAuthenticationDetails) authentication.getDetails()).getRemoteAddress();
            opaDetails = new OpaRequestDetails(remoteAddress);
        }

        String principal;
        if (authentication instanceof AnonymousAuthenticationToken) {
            try {
                principal = om.writeValueAsString(new AnonymousPrincipal(authentication.getName()));
            } catch (JsonProcessingException e) {
                logger.error("Error during authorization process. Access will be denied.", e);
                return new AuthorizationDecision(false);
            }
        } else if (authentication instanceof CzertainlyAuthenticationToken token) {
            principal = token.getPrincipal().getRawData();
        } else {
            logger.error("Unsupported authentication type: " + authentication.getClass().getName());
            return new AuthorizationDecision(false);
        }

        List<String> urlParts = Arrays.stream(request.getRequestURI()
                        .split("/"))
                .filter(p -> !p.isBlank())
                .collect(Collectors.toList());

        OpaRequestedResource resource = new OpaRequestedResource(urlParts);

        boolean authorized = decide(principal, resource, opaDetails, request);

        return new AuthorizationDecision(authorized);
    }

    private boolean decide(String principal, OpaRequestedResource resource, OpaRequestDetails details, HttpServletRequest request) {
        try {
            if (authenticationIsAnonymous(request)) {
                boolean doNotAuthorize = doNotAuthorizeAnonymousRequestsMatcher != null && doNotAuthorizeAnonymousRequestsMatcher.matches(request);
                if (doNotAuthorize) {
                    logger.trace("Request is explicitly excluded from authorization: " + request);
                    return true;
                }
            } else if (toAuthorizeRequestsMatcher != null && !toAuthorizeRequestsMatcher.matches(request)) {
                logger.trace("Request is not subject to authorization: " + request);
                return true;
            }

            OpaResourceAccessResult result = opaClient.checkResourceAccess(OpaPolicy.ENDPOINT.policyName, resource, principal, details);
            if (result.isAuthorized()) {
                logger.trace("Access granted to endpoint: " + request.getRequestURI());
                return true;
            } else {
                logger.trace("Access denied to endpoint: " + request.getRequestURI());
                return false;
            }
        } catch (Exception e) {
            logger.error("Unable to verify access to the endpoint: " + request.getRequestURI(), e);
            return false;
        }
    }

    private boolean authenticationIsAnonymous(HttpServletRequest request) {
        return doNotAuthorizeAnonymousRequestsMatcher != null && doNotAuthorizeAnonymousRequestsMatcher.matches(request);
    }

    public void setToAuthorizeRequestsMatcher(RequestMatcher toAuthorizeRequestsMatcher) {
        this.toAuthorizeRequestsMatcher = toAuthorizeRequestsMatcher;
    }

    public void setDoNotAuthorizeAnonymousRequestsMatcher(RequestMatcher doNotAuthorizeRequestsMatcher) {
        this.doNotAuthorizeAnonymousRequestsMatcher = doNotAuthorizeRequestsMatcher;
    }
}

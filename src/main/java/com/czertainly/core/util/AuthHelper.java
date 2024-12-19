package com.czertainly.core.util;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.api.model.core.logging.enums.ActorType;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.logging.enums.OperationResult;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.czertainly.core.security.authz.OpaPolicy;
import com.czertainly.core.security.authz.SecurityResourceFilter;
import com.czertainly.core.security.authz.opa.OpaClient;
import com.czertainly.core.security.authz.opa.dto.OpaObjectAccessResult;
import com.czertainly.core.security.authz.opa.dto.OpaRequestDetails;
import com.czertainly.core.security.authz.opa.dto.OpaRequestedResource;
import com.czertainly.core.service.AuditLogService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.*;

@Component
public class AuthHelper {
    // Access control request attributes
    public static final String REQ_ATTR_RESOURCE_NAME = "INTERNAL_ATTRIB_DENIED_RESOURCE_NAME";
    public static final String REQ_ATTR_RESOURCE_ACTION_NAME = "INTERNAL_ATTRIB_DENIED_RESOURCE_ACTION_NAME";
    public static final int REQ_ATTR_ACCESS_CONTROL_SCOPE = 121;

    // system users and roles names
    public static final String SYSTEM_USER_HEADER_NAME = "systemUsername";
    public static final String USER_UUID_HEADER_NAME = "userUuid";

    public static final String LOCALHOST_USERNAME = "localhost";
    public static final String SUPERADMIN_USERNAME = "superadmin";

    public static final String ACME_USERNAME = "acme";
    public static final String SCEP_USERNAME = "scep";
    public static final String CMP_USERNAME = "cmp";

    private static final Logger logger = LoggerFactory.getLogger(AuthHelper.class);

    private OpaClient opaClient;
    private CzertainlyAuthenticationClient czertainlyAuthenticationClient;
    private static final Set<String> protocolUsers = Set.of(ACME_USERNAME, SCEP_USERNAME, CMP_USERNAME);

    @Autowired
    public void setOpaClient(OpaClient opaClient) {
        this.opaClient = opaClient;
    }

    @Autowired
    public void setCzertainlyAuthenticationClient(CzertainlyAuthenticationClient czertainlyAuthenticationClient) {
        this.czertainlyAuthenticationClient = czertainlyAuthenticationClient;
    }

    public void authenticateAsSystemUser(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SYSTEM_USER_HEADER_NAME, username);

        // update MDC for actor logging
        ActorType actorType = protocolUsers.contains(username) ? ActorType.PROTOCOL : ActorType.CORE;
        LoggingHelper.putActorInfoWhenNull(actorType, null, username);

        AuthenticationInfo authUserInfo = czertainlyAuthenticationClient.authenticate(headers, false);
        CzertainlyUserDetails userDetails = new CzertainlyUserDetails(authUserInfo);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(new CzertainlyAuthenticationToken(userDetails));
        logger.debug("User with username '{}' has been successfully authenticated as system user proxy.", authUserInfo.getUsername());
    }

    public void authenticateAsUser(UUID userUuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(USER_UUID_HEADER_NAME, userUuid.toString());

        // update MDC for actor logging
        LoggingHelper.putActorInfoWhenNull(ActorType.USER, userUuid.toString(), null);

        AuthenticationInfo authUserInfo = czertainlyAuthenticationClient.authenticate(headers, false);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authUserInfo)));
        logger.debug("User with username '{}' has been successfully authenticated as user proxy.", authUserInfo.getUsername());
    }

    public static boolean isLoggedProtocolUser() {
        try {
            CzertainlyUserDetails userDetails = (CzertainlyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String username = userDetails.getUsername();
            return protocolUsers.contains(username);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ValidationException(ValidationError.create("Cannot retrieve information of logged protocol user for Unknown/Anonymous user"));
        }
    }

    public static NameAndUuidDto getUserIdentification() throws ValidationException {
        try {
            CzertainlyUserDetails userDetails = (CzertainlyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return new NameAndUuidDto(userDetails.getUserUuid(), userDetails.getUsername());
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ValidationException(ValidationError.create("Cannot retrieve user identification for Unknown/Anonymous user"));
        }
    }

    public static UserProfileDto getUserProfile() {
        UserProfileDto userProfileDto;
        try {
            CzertainlyUserDetails userDetails = (CzertainlyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            userProfileDto = objectMapper.readValue(userDetails.getRawData(), UserProfileDto.class);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ValidationException(ValidationError.create("Cannot retrieve profile information for Unknown/Anonymous user"));
        }
        return userProfileDto;
    }

    public SecurityResourceFilter loadObjectPermissions(Resource resource, ResourceAction resourceAction) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof CzertainlyAuthenticationToken czertainlyAuthenticationToken)) {
            // return filter with empty permissions (no objects allowed)
            return new SecurityResourceFilter(List.of(), List.of(), true);
        }

        Map<String, String> properties = Map.of("name", resource.getCode(), "action", resourceAction.getCode());
        OpaRequestedResource resourceProps = new OpaRequestedResource(properties);
        OpaObjectAccessResult result = opaClient.checkObjectAccess(OpaPolicy.OBJECTS.policyName, resourceProps, czertainlyAuthenticationToken.getPrincipal().getRawData(), new OpaRequestDetails(null));

        SecurityResourceFilter resourceFilter = SecurityResourceFilter.create();
        resourceFilter.setResource(resource);
        resourceFilter.setResourceAction(resourceAction);
        resourceFilter.addAllowedObjects(result.getAllowedObjects());
        resourceFilter.addDeniedObjects(result.getForbiddenObjects());
        resourceFilter.setAreOnlySpecificObjectsAllowed(!result.isActionAllowedForGroupOfObjects());
        return resourceFilter;
    }

    // Method to handle extracting the client IP, even if behind proxies
    public static String getClientIPAddress(HttpServletRequest request) {
        String ipAddress = null;
        List<String> proxyHeaders = List.of("X-Forwarded-For", "X-Real-IP", "HTTP_X_FORWARDED_FOR", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP");

        for (String proxyHeader : proxyHeaders) {
            ipAddress = request.getHeader(proxyHeader);
            if (ipAddress != null && !ipAddress.isBlank() && !ipAddress.equalsIgnoreCase("unknown")) {
                break;
            }
        }

        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }

        // In case of multiple proxies, the first IP in the list is the real client IP
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0];
        }
        return ipAddress;
    }

    public static String getDeniedPermissionResource() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Object requestAttribute = requestAttributes == null ? null : requestAttributes.getAttribute(REQ_ATTR_RESOURCE_NAME, REQ_ATTR_ACCESS_CONTROL_SCOPE);

        return requestAttribute == null ? null : requestAttribute.toString();
    }

    public static String getDeniedPermissionResourceAction() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Object requestAttribute = requestAttributes == null ? null : requestAttributes.getAttribute(REQ_ATTR_RESOURCE_ACTION_NAME, REQ_ATTR_ACCESS_CONTROL_SCOPE);

        return requestAttribute == null ? null : requestAttribute.toString();
    }

    public static void setDeniedPermissionResourceAction(String resourceName, String resourceActionName) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            requestAttributes.setAttribute(REQ_ATTR_RESOURCE_NAME, resourceName, REQ_ATTR_ACCESS_CONTROL_SCOPE);
            requestAttributes.setAttribute(REQ_ATTR_RESOURCE_ACTION_NAME, resourceActionName, REQ_ATTR_ACCESS_CONTROL_SCOPE);
        }
    }

    public static void logAndAuditAuthFailure(Logger logger, AuditLogService auditLogService, String message, String authData) {
        if (logger.isDebugEnabled()) {
            logger.debug("{}: {}", message, authData);
        } else {
            logger.info(message);
        }
        auditLogService.logAuthentication(Operation.AUTHENTICATION, OperationResult.FAILURE, message, authData);
    }
}

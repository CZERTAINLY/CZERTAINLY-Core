package com.czertainly.core.util;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthHelper {

    public static final String SYSTEM_USER_HEADER_NAME = "systemUsername";
    public static final String USER_UUID_HEADER_NAME = "userUuid";
    public static final String ACME_USERNAME = "acme";
    public static final String SCEP_USERNAME = "scep";

    private static final Logger logger = LoggerFactory.getLogger(AuthHelper.class);

    private static CzertainlyAuthenticationClient czertainlyAuthenticationClient;

    @Autowired
    public AuthHelper(CzertainlyAuthenticationClient czertainlyAuthenticationClient) {
        AuthHelper.czertainlyAuthenticationClient = czertainlyAuthenticationClient;
    }

    public static void authenticateAsSystemUser(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SYSTEM_USER_HEADER_NAME, username);

        AuthenticationInfo authUserInfo = czertainlyAuthenticationClient.authenticate(headers);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authUserInfo)));
    }

    public static void authenticateAsUser(UUID userUuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(USER_UUID_HEADER_NAME, userUuid.toString());

        AuthenticationInfo authUserInfo = czertainlyAuthenticationClient.authenticate(headers);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(new CzertainlyAuthenticationToken(new CzertainlyUserDetails(authUserInfo)));
    }

    public static boolean isLoggedProtocolUser() {
        try {
            CzertainlyUserDetails userDetails = (CzertainlyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String username = userDetails.getUsername();
            return username.equals(ACME_USERNAME) || username.equals(SCEP_USERNAME);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ValidationException(ValidationError.create("Cannot retrieve information of logged protocol user for Unknown/Anonymous user"));
        }
    }

    public static NameAndUuidDto getUserIdentification() {
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
}

package com.czertainly.core.util;

import com.czertainly.core.security.authn.CzertainlyAuthenticationToken;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import com.czertainly.core.security.authn.client.AuthenticationInfo;
import com.czertainly.core.security.authn.client.CzertainlyAuthenticationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
}

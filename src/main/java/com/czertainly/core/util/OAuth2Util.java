package com.czertainly.core.util;

import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.text.ParseException;
import java.util.List;

public class OAuth2Util {

    private OAuth2Util() {
        throw new IllegalStateException("Utility class");
    }

    public static void validateAudiences(OAuth2AccessToken accessToken, OAuth2ProviderSettingsDto providerSettings) {

        List<String> clientAudiences = providerSettings.getAudiences();
        List<String> tokenAudiences;
        try {
            tokenAudiences = SignedJWT.parse(accessToken.getTokenValue()).getJWTClaimsSet().getAudience();
        } catch (ParseException e) {
            throw new CzertainlyAuthenticationException("Could not parse JWT Access Token.");
        }

        if (!(clientAudiences == null || clientAudiences.isEmpty() || tokenAudiences != null && tokenAudiences.stream().anyMatch(clientAudiences::contains))) {
            String errorMessage = "User was not authenticated: audiences in access token issued by OAuth2 Provider do not match any of audiences set for the provider in settings.";
            throw new CzertainlyAuthenticationException(errorMessage);
        }

    }
}

package com.czertainly.core.security.oauth2;


import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.core.security.authn.CzertainlyAuthenticationException;
import com.czertainly.core.util.OAuth2Util;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class OAuth2UtilTest {

    @Test
    void testValidateAudiences() throws NoSuchAlgorithmException, JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        OAuth2ProviderSettingsDto providerSettingsDto = new OAuth2ProviderSettingsDto();
        OAuth2AccessToken accessTokenCorrectAudience = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, OAuth2TestUtil.createJwtTokenValue(keyPair.getPrivate(), 1, null, "expected"), Instant.now(), Instant.now().plusMillis(200));
        OAuth2AccessToken accessTokenIncorrectAudience = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, OAuth2TestUtil.createJwtTokenValue(keyPair.getPrivate(), 1, null, "unexpected"), Instant.now(), Instant.now().plusMillis(200));


        providerSettingsDto.setAudiences(List.of("expected"));
        Assertions.assertDoesNotThrow(() -> OAuth2Util.validateAudiences(accessTokenCorrectAudience, providerSettingsDto));

        providerSettingsDto.setAudiences(List.of("expected", "other"));
        Assertions.assertDoesNotThrow(() -> OAuth2Util.validateAudiences(accessTokenCorrectAudience, providerSettingsDto));

        Assertions.assertThrows(CzertainlyAuthenticationException.class, () -> OAuth2Util.validateAudiences(accessTokenIncorrectAudience, providerSettingsDto));

        providerSettingsDto.setAudiences(new ArrayList<>());
        Assertions.assertDoesNotThrow(() -> OAuth2Util.validateAudiences(accessTokenIncorrectAudience, providerSettingsDto));

    }


}

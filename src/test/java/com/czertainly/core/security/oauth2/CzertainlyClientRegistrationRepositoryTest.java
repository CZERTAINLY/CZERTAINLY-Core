package com.czertainly.core.security.oauth2;

import com.czertainly.api.model.core.settings.OAuth2ProviderSettingsDto;
import com.czertainly.core.auth.oauth2.CzertainlyClientRegistrationRepository;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CzertainlyClientRegistrationRepositoryTest extends BaseSpringBootTest {

    @Autowired
    private CzertainlyClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private SettingService settingService;

    @BeforeEach
    void setUp() {
        OAuth2ProviderSettingsDto providerSettings = new OAuth2ProviderSettingsDto();
        providerSettings.setClientSecret("secret");
        providerSettings.setIssuerUrl("issuer");
        providerSettings.setClientId("client");
        providerSettings.setClientSecret("secret");
        providerSettings.setAuthorizationUrl("http");
        providerSettings.setTokenUrl("http");
        settingService.updateOAuth2ProviderSettings("provider", providerSettings);
    }

    @Test
    void testRetrieveClientRegistration() {
        Assertions.assertNotNull(clientRegistrationRepository.findByRegistrationId("provider"));
        Assertions.assertNull(clientRegistrationRepository.findByRegistrationId("non-existing"));
    }
}


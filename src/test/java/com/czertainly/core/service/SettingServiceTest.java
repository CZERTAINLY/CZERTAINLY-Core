package com.czertainly.core.service;

import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingServiceTest extends BaseSpringBootTest {

    @Autowired
    private SettingService settingService;

    @Test
    void updatePlatformSettings() {
        String utilsServiceUrl = "http://util-service:8080";

        PlatformSettingsDto platformSettings = settingService.getPlatformSettings();
        Assertions.assertNull(platformSettings.getUtils().getUtilsServiceUrl());
        Assertions.assertNotNull(platformSettings.getCertificates());
        Assertions.assertTrue(platformSettings.getCertificates().getValidationEnabled());
        Assertions.assertEquals(1, platformSettings.getCertificates().getValidationFrequency());

        platformSettings.getUtils().setUtilsServiceUrl(utilsServiceUrl);
        platformSettings.getCertificates().setValidationFrequency(5);
        settingService.updatePlatformSettings(platformSettings);

        platformSettings = settingService.getPlatformSettings();
        Assertions.assertEquals(utilsServiceUrl, platformSettings.getUtils().getUtilsServiceUrl());
        Assertions.assertEquals(5, platformSettings.getCertificates().getValidationFrequency());
    }
}

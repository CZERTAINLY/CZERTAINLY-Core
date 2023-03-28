package com.czertainly.core.service;

import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SettingServiceTest extends BaseSpringBootTest {

    @Autowired
    private SettingService settingService;

    @BeforeEach
    public void setUp() {
    }

    @Test
    public void updatePlatformSettings() {
        String utilsServiceUrl = "http://util-service:8080";

        PlatformSettingsDto platformSettings = settingService.getPlatformSettings();
        Assertions.assertNull(platformSettings.getUtils().getUtilsServiceUrl());

        platformSettings.getUtils().setUtilsServiceUrl(utilsServiceUrl);
        settingService.updatePlatformSettings(platformSettings);

        platformSettings = settingService.getPlatformSettings();
        Assertions.assertEquals(utilsServiceUrl, platformSettings.getUtils().getUtilsServiceUrl());
    }
}

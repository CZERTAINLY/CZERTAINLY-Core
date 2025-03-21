package com.czertainly.core.service;

import com.czertainly.api.model.core.settings.*;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.SettingRepository;
import com.czertainly.core.service.impl.SettingServiceImpl;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingServiceTest extends BaseSpringBootTest {

    @Autowired
    private SettingService settingService;
    @Autowired
    private SettingRepository settingRepository;

    @Test
    void updatePlatformSettings() {
        String utilsServiceUrl = "http://util-service:8080";

        PlatformSettingsDto platformSettings = settingService.getPlatformSettings();
        Assertions.assertNull(platformSettings.getUtils().getUtilsServiceUrl());
        Assertions.assertNotNull(platformSettings.getCertificates());
        Assertions.assertTrue(platformSettings.getCertificates().getValidation().getEnabled());
        Assertions.assertEquals(1, platformSettings.getCertificates().getValidation().getFrequency());

        PlatformSettingsUpdateDto platformSettingsUpdateDto = new PlatformSettingsUpdateDto();
        UtilsSettingsDto utilsSettingsDto = new UtilsSettingsDto();
        utilsSettingsDto.setUtilsServiceUrl(utilsServiceUrl);
        platformSettingsUpdateDto.setUtils(utilsSettingsDto);
        CertificateSettingsUpdateDto certificateSettingsUpdateDto = new CertificateSettingsUpdateDto();
        CertificateValidationSettingsUpdateDto certificateValidationSettingsUpdateDto = new CertificateValidationSettingsUpdateDto();
        certificateValidationSettingsUpdateDto.setFrequency(5);
        certificateSettingsUpdateDto.setValidation(certificateValidationSettingsUpdateDto);
        platformSettingsUpdateDto.setCertificates(certificateSettingsUpdateDto);
        settingService.updatePlatformSettings(platformSettingsUpdateDto);

        platformSettings = settingService.getPlatformSettings();
        Assertions.assertEquals(utilsServiceUrl, platformSettings.getUtils().getUtilsServiceUrl());
        Assertions.assertEquals(5, platformSettings.getCertificates().getValidation().getFrequency());
    }

    @Test
    void testPlatformSettingsExceptions() {
        Setting setting = new Setting();
        setting.setSection(SettingsSection.PLATFORM);
        setting.setName(SettingServiceImpl.CERTIFICATES_VALIDATION_SETTINGS_NAME);
        setting.setCategory(SettingsSectionCategory.PLATFORM_CERTIFICATES.getCode());
        setting.setValue("invalid");
        settingRepository.save(setting);
        PlatformSettingsDto platformSettingsDto = settingService.getPlatformSettings();
        Assertions.assertNotNull(platformSettingsDto.getCertificates().getValidation());
    }
}

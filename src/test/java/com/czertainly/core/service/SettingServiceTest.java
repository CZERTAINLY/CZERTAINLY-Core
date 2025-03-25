package com.czertainly.core.service;

import com.czertainly.api.model.core.settings.*;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsResponseDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsUpdateDto;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.SettingRepository;
import com.czertainly.core.service.impl.SettingServiceImpl;
import com.czertainly.core.util.BaseSpringBootTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.util.Base64;
import java.util.List;

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

    @Test
    void testRetrievingJwkSet() throws JsonProcessingException, JOSEException, ParseException {

        RSAKey rsaJwk = new RSAKeyGenerator(2048)
                .generate();

        ECKey ecJwk = new ECKeyGenerator(Curve.P_256)
                .generate();

        OctetSequenceKey aesJwk = new OctetSequenceKeyGenerator(128)
                .generate();

        OctetKeyPair octetKeyPairJwk = OctetKeyPair.parse("{\"kty\":\"OKP\",\"d\":\"y85lxYiKx57Dwgs2rH1b0yTVxeLJWmpt48WfivLXBbU\",\"crv\":\"Ed25519\",\"x\":\"tO6vOgx0YOVuWdevkbYzaihxcCLx8DfqRs2nvs3CBxU\", \"kid\": \"123\"}");

        JWKSet jwkSet = new JWKSet(List.of(rsaJwk, ecJwk, aesJwk, octetKeyPairJwk));

        OAuth2ProviderSettingsUpdateDto providerSettings = new OAuth2ProviderSettingsUpdateDto();
        ObjectMapper objectMapper = new ObjectMapper();

        providerSettings.setJwkSet(Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(jwkSet.toJSONObject(false)).getBytes()));
        Setting oauth2Setting = new Setting();
        oauth2Setting.setValue(objectMapper.writeValueAsString(providerSettings));
        oauth2Setting.setName("name");
        oauth2Setting.setSection(SettingsSection.AUTHENTICATION);
        oauth2Setting.setCategory(SettingsSectionCategory.OAUTH2_PROVIDER.getCode());
        settingRepository.save(oauth2Setting);

        OAuth2ProviderSettingsResponseDto oAuth2ProvidersSettingsUpdateDto = settingService.getOAuth2ProviderSettings("name", false);
        Assertions.assertEquals(Base64.getEncoder().encodeToString(rsaJwk.toPublicKey().getEncoded()), oAuth2ProvidersSettingsUpdateDto.getJwkSetKeys().stream().filter(jwk -> jwk.getKeyType().equals("RSA")).findFirst().get().getPublicKey());
        Assertions.assertEquals(Base64.getEncoder().encodeToString(ecJwk.toPublicKey().getEncoded()), oAuth2ProvidersSettingsUpdateDto.getJwkSetKeys().stream().filter(jwk -> jwk.getKeyType().equals("EC")).findFirst().get().getPublicKey());
        Assertions.assertEquals(Base64.getEncoder().encodeToString(aesJwk.toByteArray()), oAuth2ProvidersSettingsUpdateDto.getJwkSetKeys().stream().filter(jwk -> jwk.getKeyType().equals("oct")).findFirst().get().getPublicKey());
        Assertions.assertEquals(Base64.getEncoder().encodeToString(octetKeyPairJwk.getDecodedX()), oAuth2ProvidersSettingsUpdateDto.getJwkSetKeys().stream().filter(jwk -> jwk.getKeyType().equals("OKP")).findFirst().get().getPublicKey());
    }

}

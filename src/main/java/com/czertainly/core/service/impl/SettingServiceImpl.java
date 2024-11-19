package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.settings.*;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.SettingRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.SecretEncodingVersion;
import com.czertainly.core.util.SecretsUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Transactional
public class SettingServiceImpl implements SettingService {
    public static final String UTILS_SERVICE_URL_NAME = "utilsServiceUrl";
    public static final String NOTIFICATIONS_MAPPING_NAME = "notificationsMapping";

    public static final String OAUTH2_PROVIDER_CATEGORY = "oauth2Provider";

    private static final Logger logger = LoggerFactory.getLogger(SettingServiceImpl.class);

    private SettingRepository settingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public void setSettingRepository(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public PlatformSettingsDto getPlatformSettings() {
        List<Setting> settings = settingRepository.findBySection(SettingsSection.PLATFORM);
        Map<String, Map<String, Setting>> mappedSettings = mapSettingsByCategory(settings);

        PlatformSettingsDto platformSettings = new PlatformSettingsDto();

        // utils
        Map<String, Setting> utilsSettings = mappedSettings.get("utils");
        UtilsSettingsDto utilsSettingsDto = new UtilsSettingsDto();
        if (utilsSettings != null)
            utilsSettingsDto.setUtilsServiceUrl(utilsSettings.get(UTILS_SERVICE_URL_NAME).getValue());
        platformSettings.setUtils(utilsSettingsDto);

        return platformSettings;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void updatePlatformSettings(PlatformSettingsDto platformSettings) {
        List<Setting> settings = settingRepository.findBySection(SettingsSection.PLATFORM);
        Map<String, Map<String, Setting>> mappedSettings = mapSettingsByCategory(settings);

        // utils
        Setting setting = null;
        Map<String, Setting> utilsSettings = mappedSettings.get("utils");
        if (utilsSettings == null || (setting = utilsSettings.get(UTILS_SERVICE_URL_NAME)) == null) {
            setting = new Setting();
            setting.setSection(SettingsSection.PLATFORM);
            setting.setCategory("utils");
            setting.setName(UTILS_SERVICE_URL_NAME);
        }

        setting.setValue(platformSettings.getUtils().getUtilsServiceUrl());
        settingRepository.save(setting);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public NotificationSettingsDto getNotificationSettings() {
        List<Setting> settings = settingRepository.findBySection(SettingsSection.NOTIFICATIONS);
        Map<NotificationType, String> valueMapped = new HashMap<>();
        if (!settings.isEmpty()) {
            String valueJson = settings.get(0).getValue();
            if (valueJson != null) {
                TypeReference<Map<NotificationType, String>> typeReference = new TypeReference<>() {
                };

                try {
                    valueMapped = objectMapper.readValue(valueJson, typeReference);
                } catch (JsonProcessingException e) {
                    throw new ValidationException("Could not convert JSON to value.");
                }
            }
        }

        NotificationSettingsDto notificationSettings = new NotificationSettingsDto();
        notificationSettings.setNotificationsMapping(valueMapped);

        return notificationSettings;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void updateNotificationSettings(NotificationSettingsDto notificationSettings) {

        List<Setting> settings = settingRepository.findBySection(SettingsSection.NOTIFICATIONS);
        Setting setting;
        if (settings.isEmpty()) {
            setting = new Setting();
            setting.setSection(SettingsSection.NOTIFICATIONS);
            setting.setName(NOTIFICATIONS_MAPPING_NAME);
        } else {
            setting = settings.get(0);
        }

        Map<NotificationType, String> notificationTypeStringMap = notificationSettings.getNotificationsMapping();
        for (String uuid : notificationTypeStringMap.values()) {
            try {
                UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid UUID of a notification instance reference.");
            }
        }
        try {
            setting.setValue(objectMapper.writeValueAsString(notificationTypeStringMap));
        } catch (JsonProcessingException e) {
            throw new ValidationException("Could not convert JSON to value.");
        }
        settingRepository.save(setting);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public OAuth2ProviderSettings getOAuth2ProviderSettings(String providerName, boolean withClientSecret) {
        List<Setting> settings = settingRepository.findByCategoryAndSectionAndName(OAUTH2_PROVIDER_CATEGORY, SettingsSection.AUTHENTICATION, providerName);
        OAuth2ProviderSettings settingsDto = null;
        if (!settings.isEmpty()) {
            Setting setting = settings.getFirst();
            try {
                settingsDto = objectMapper.readValue(setting.getValue(), OAuth2ProviderSettings.class);
                if (!withClientSecret) settingsDto.setClientSecret(null);
            } catch (JsonProcessingException e) {
                throw new ValidationException("Unable to convert JSON value to OAuth2 Provider Settings object.");
            }
        }
        return settingsDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void updateOAuth2ProviderSettings(String providerName, OAuth2ProviderSettings settingsDto) {

        List<Setting> settingForRegistrationId = settingRepository.findByCategoryAndSectionAndName(OAUTH2_PROVIDER_CATEGORY, SettingsSection.AUTHENTICATION, providerName);
        Setting setting = settingForRegistrationId.isEmpty() ? new Setting() : settingForRegistrationId.getFirst();
        setting.setName(providerName);
        setting.setCategory(OAUTH2_PROVIDER_CATEGORY);
        settingsDto.setClientSecret(SecretsUtil.encryptAndEncodeSecretString(settingsDto.getClientSecret(), SecretEncodingVersion.V1));
        try {
            setting.setValue(objectMapper.writeValueAsString(settingsDto));
        } catch (JsonProcessingException e) {
            throw new ValidationException("Unable to convert settings to JSON.");
        }
        setting.setSection(SettingsSection.AUTHENTICATION);
        settingRepository.save(setting);
    }

    @Override
    public List<String> listNamesOfOAuth2Providers() {
        return settingRepository.findBySection(SettingsSection.AUTHENTICATION).stream().map(Setting::getName).toList();
    }

    @Override
    public OAuth2ProviderSettings findOAuth2ProviderByIssuerUri(String issuerUri) {
        Setting setting = settingRepository.findBySection(SettingsSection.AUTHENTICATION).stream()
                .filter(s -> {
                    try {
                        return objectMapper.readValue(s.getValue(), OAuth2ProviderSettings.class).getIssuerUrl().equals(issuerUri);
                    } catch (JsonProcessingException e) {
                        return false;
                    }
                }).findFirst().orElse(null);
        if (setting == null) return null;
        try {
            return objectMapper.readValue(setting.getValue(), OAuth2ProviderSettings.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Could not process JSON value of OAuth2 Provider.");
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void removeOAuth2Provider(String providerName) {
        List<Setting> settings = settingRepository.findByCategoryAndSectionAndName(OAUTH2_PROVIDER_CATEGORY, SettingsSection.AUTHENTICATION, providerName);
        if (!settings.isEmpty()) settingRepository.delete(settings.getFirst());
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public List<OAuth2SettingsDto> listOAuth2Providers() {
        List<String> providerNames = listNamesOfOAuth2Providers();
        List<OAuth2SettingsDto> settingsDtoList = new ArrayList<>();
        for (String providerName: providerNames) {
            OAuth2SettingsDto settingsDto = new OAuth2SettingsDto();
            settingsDto.setProviderName(providerName);
            settingsDto.setOAuth2ProviderSettings(getOAuth2ProviderSettings(providerName, false));
            settingsDtoList.add(settingsDto);
        }
        return settingsDtoList;
    }

    private Map<String, Map<String, Setting>> mapSettingsByCategory(List<Setting> settings) {
        var mapping = new HashMap<String, Map<String, Setting>>();

        for (Setting setting : settings) {
            String category = setting.getCategory();
            Map<String, Setting> categorySettings = mapping.computeIfAbsent(category, k -> new HashMap<>());
            categorySettings.put(setting.getName(), setting);
        }

        return mapping;
    }

}

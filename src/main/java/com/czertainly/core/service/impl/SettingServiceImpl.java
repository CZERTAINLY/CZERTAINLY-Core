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

    private static final Logger logger = LoggerFactory.getLogger(SettingServiceImpl.class);

    private SettingRepository settingRepository;

    @Autowired
    public void setSettingRepository(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
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
    public NotificationSettingsDto getNotificationSettings() {
        List<Setting> settings = settingRepository.findBySection(SettingsSection.NOTIFICATIONS);
        Map<NotificationType, String> valueMapped = new HashMap<>();
        if (!settings.isEmpty()) {
            String valueJson = settings.get(0).getValue();
            if (valueJson != null) {
                ObjectMapper mapper = new ObjectMapper();
                TypeReference<Map<NotificationType, String>> typeReference = new TypeReference<>() {
                };

                try {
                    valueMapped = mapper.readValue(valueJson, typeReference);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
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

        ObjectMapper mapper = new ObjectMapper();
        Map<NotificationType, String> notificationTypeStringMap = notificationSettings.getNotificationsMapping();
        for (String uuid : notificationTypeStringMap.values()) {
            try {
                UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid UUID of a notification instance reference.");
            }
        }
        try {
            setting.setValue(mapper.writeValueAsString(notificationTypeStringMap));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        settingRepository.save(setting);
    }

    @Override
    public List<Oauth2SettingsDto> getOauth2ProviderSettings() {
        List<Oauth2SettingsDto> oauth2SettingsDtos = new ArrayList<>();
        List<Setting> settings = settingRepository.findBySection(SettingsSection.OAUTH2_PROVIDER);
        if (!settings.isEmpty()) {
            for (Setting setting : settings) {
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    Oauth2SettingsDto settingsDto = new Oauth2SettingsDto();
                    settingsDto.setClientSettings(objectMapper.readValue(setting.getValue(), Oauth2ClientSettings.class));
                    settingsDto.setRegistrationId(setting.getName());
                    oauth2SettingsDtos.add(settingsDto);
                } catch (JsonProcessingException e) {
                    throw new ValidationException("Unable to convert JSON value to Client Settings object.");
                }
            }
         }
        return oauth2SettingsDtos;
    }

    @Override
    public void updateOauth2ProviderSettings(List<Oauth2SettingsDto> oauth2SettingsDto) {
        for (Oauth2SettingsDto settingsDto : oauth2SettingsDto) {
            if (settingsDto.getRegistrationId() == null) {
                throw new ValidationException("Registration ID cannot be null."); // checks for other properties too
            }
            List<Setting> settingForRegistrationId = settingRepository.findBySectionAndName(SettingsSection.OAUTH2_PROVIDER, settingsDto.getRegistrationId());
            Setting setting = settingForRegistrationId.isEmpty() ? new Setting() : settingForRegistrationId.getFirst();
            setting.setName(settingsDto.getRegistrationId());
            ObjectMapper mapper = new ObjectMapper();
            try {
                setting.setValue(mapper.writeValueAsString(settingsDto.getClientSettings()));
            } catch (JsonProcessingException e) {
                throw new ValidationException("Unable to convert settings to JSON.");
            }
            setting.setSection(SettingsSection.OAUTH2_PROVIDER);
            settingRepository.save(setting);
        }
    }

    @Override
    public List<String> getListOfOauth2Clients() {
        return settingRepository.findBySection(SettingsSection.OAUTH2_PROVIDER).stream().map(Setting::getName).toList();
    }

    @Override
    public Oauth2ResourceServerSettingsDto getOauth2ResourceServerSettings() {
        Oauth2ResourceServerSettingsDto resourceServerSettingsDto = new Oauth2ResourceServerSettingsDto();
        List<Setting> settings = settingRepository.findBySection(SettingsSection.OAUTH2_RESOURCE_SERVER);
        if (!settings.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                resourceServerSettingsDto = objectMapper.readValue(settings.getFirst().getValue(), Oauth2ResourceServerSettingsDto.class);
            } catch (JsonProcessingException e) {
                throw new ValidationException("Unable to convert JSON value to Resource Server object.");
            }
        }

        return resourceServerSettingsDto;
    }

    @Override
    public void updateOauth2ResourceServerSettings(Oauth2ResourceServerSettingsDto oauth2ResourceServerSettingsDto) {
        if (oauth2ResourceServerSettingsDto.getIssuerUri() == null) {
            throw new ValidationException("Issuer URI cannot be null");
        }
        List<Setting> settings = settingRepository.findBySection(SettingsSection.OAUTH2_RESOURCE_SERVER);
        Setting setting = settings.isEmpty() ? new Setting() : settings.getFirst();

        setting.setSection(SettingsSection.OAUTH2_RESOURCE_SERVER);
        ObjectMapper mapper = new ObjectMapper();
        try {
            setting.setValue(mapper.writeValueAsString(oauth2ResourceServerSettingsDto));
            setting.setName("name");
            settingRepository.save(setting);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Unable to convert settings to json.");
        }
    }

    private Map<String, Map<String, Setting>> mapSettingsByCategory(List<Setting> settings) {
        var mapping = new HashMap<String, Map<String, Setting>>();

        for (Setting setting : settings) {
            Map<String, Setting> categorySettings;
            if ((categorySettings = mapping.get(setting.getCategory())) == null)
                mapping.put(setting.getCategory(), categorySettings = new HashMap<>());
            categorySettings.put(setting.getName(), setting);
        }

        return mapping;
    }

}

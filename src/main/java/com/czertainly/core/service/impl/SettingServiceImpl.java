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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        if(utilsSettings != null) utilsSettingsDto.setUtilsServiceUrl(utilsSettings.get(UTILS_SERVICE_URL_NAME).getValue());
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
            if(valueJson != null) {
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
            }
            catch (IllegalArgumentException e) {
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
    public Oauth2SettingsDto getOauth2ProviderSettings() {
        return null;
    }

    @Override
    public void updateOauth2ProviderSettings(Oauth2SettingsDto oauth2SettingsDto) {
        Setting setting = new Setting();
        setting.setName(oauth2SettingsDto.getName());
        setting.setValue(oauth2SettingsDto.getJson());
        setting.setSection(SettingsSection.OAUTH2_PROVIDER);
        settingRepository.save(setting);
    }

    private Map<String, Map<String, Setting>> mapSettingsByCategory(List<Setting> settings) {
        var mapping = new HashMap<String, Map<String, Setting>>();

        for (Setting setting: settings) {
            Map<String, Setting> categorySettings;
            if((categorySettings = mapping.get(setting.getCategory())) == null) mapping.put(setting.getCategory(), categorySettings = new HashMap<>());
            categorySettings.put(setting.getName(), setting);
        }

        return mapping;
    }

}

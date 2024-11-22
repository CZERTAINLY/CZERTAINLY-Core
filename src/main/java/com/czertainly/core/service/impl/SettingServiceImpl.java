package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.AuditLogOutput;
import com.czertainly.api.model.core.settings.*;
import com.czertainly.api.model.core.settings.logging.AuditLoggingSettingsDto;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;
import com.czertainly.api.model.core.settings.logging.ResourceLoggingSettingsDto;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.SettingRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.SecretEncodingVersion;
import com.czertainly.core.util.SecretsUtil;
import com.czertainly.core.settings.SettingsCache;
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
    public static final String LOGGING_AUDIT_LOG_OUTPUT_NAME = "output";
    public static final String LOGGING_RESOURCES_NAME = "resources";

    private static final Logger logger = LoggerFactory.getLogger(SettingServiceImpl.class);

    private final ObjectMapper mapper;
    private final SettingsCache settingsCache;
    private final SettingRepository settingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public SettingServiceImpl(SettingsCache settingsCache, SettingRepository settingRepository, ObjectMapper mapper) {
        this.mapper = mapper;
        this.settingsCache = settingsCache;
        this.settingRepository = settingRepository;

        settingsCache.cacheSettings(SettingsSection.PLATFORM, getPlatformSettings());
        settingsCache.cacheSettings(SettingsSection.LOGGING, getLoggingSettings());
        settingsCache.cacheSettings(SettingsSection.NOTIFICATIONS, getNotificationSettings());
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public PlatformSettingsDto getPlatformSettings() {
        List<Setting> settings = settingRepository.findBySection(SettingsSection.PLATFORM);
        Map<String, Map<String, Setting>> mappedSettings = mapSettingsByCategory(settings);

        PlatformSettingsDto platformSettings = new PlatformSettingsDto();

        // utils
        Map<String, Setting> utilsSettings = mappedSettings.get(SettingsSectionCategory.PLATFORM_UTILS.getCode());
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
        Map<String, Setting> utilsSettings = mappedSettings.get(SettingsSectionCategory.PLATFORM_UTILS.getCode());
        if (utilsSettings == null || (setting = utilsSettings.get(UTILS_SERVICE_URL_NAME)) == null) {
            setting = new Setting();
            setting.setSection(SettingsSection.PLATFORM);
            setting.setCategory(SettingsSectionCategory.PLATFORM_UTILS.getCode());
            setting.setName(UTILS_SERVICE_URL_NAME);
        }

        setting.setValue(platformSettings.getUtils().getUtilsServiceUrl());
        settingRepository.save(setting);

        settingsCache.cacheSettings(SettingsSection.PLATFORM, platformSettings);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public NotificationSettingsDto getNotificationSettings() {
        List<Setting> settings = settingRepository.findBySection(SettingsSection.NOTIFICATIONS);
        Map<NotificationType, String> valueMapped = new EnumMap<>(NotificationType.class);
        if (!settings.isEmpty()) {
            String valueJson = settings.getFirst().getValue();
            if (valueJson != null) {
                TypeReference<Map<NotificationType, String>> typeReference = new TypeReference<>() {
                };

                try {
                    valueMapped = objectMapper.readValue(valueJson, typeReference);
                } catch (JsonProcessingException e) {
                    logger.warn("Cannot deserialize notification mapping settings. Returning empty mapping.");
                    valueMapped = new EnumMap<>(NotificationType.class);
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
            setting = settings.getFirst();
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
            throw new ValidationException("Cannot serialize notification mapping settings: " + e.getMessage());
        }
        settingRepository.save(setting);
        settingsCache.cacheSettings(SettingsSection.NOTIFICATIONS, notificationSettings);

    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public LoggingSettingsDto getLoggingSettings() {
        LoggingSettingsDto loggingSettingsDto = new LoggingSettingsDto();
        List<Setting> settings = settingRepository.findBySection(SettingsSection.LOGGING);
        Map<String, Map<String, Setting>> mappedSettings = mapSettingsByCategory(settings);

        // audit logging
        Setting setting;
        Map<String, Setting> auditLoggingSettings = mappedSettings.get(SettingsSectionCategory.AUDIT_LOGGING.getCode());
        AuditLoggingSettingsDto auditLoggingSettingsDto = new AuditLoggingSettingsDto();
        if (auditLoggingSettings != null) {
            if ((setting = auditLoggingSettings.get(LOGGING_AUDIT_LOG_OUTPUT_NAME)) != null) {
                auditLoggingSettingsDto.setOutput(AuditLogOutput.valueOf(setting.getValue()));
            }
            if ((setting = auditLoggingSettings.get(LOGGING_RESOURCES_NAME)) != null) {
                ResourceLoggingSettingsDto resources;
                try {
                    resources = mapper.readValue(setting.getValue(), ResourceLoggingSettingsDto.class);
                } catch (JsonProcessingException e) {
                    logger.warn("Cannot deserialize audit logs resource settings. Returning default settings.");
                    resources = new ResourceLoggingSettingsDto();
                }
                auditLoggingSettingsDto.setResourceLogging(resources);
            }
        }
        loggingSettingsDto.setAuditLogs(auditLoggingSettingsDto);

        // event logging
        Map<String, Setting> eventLoggingSettings = mappedSettings.get(SettingsSectionCategory.EVENT_LOGGING.getCode());
        ResourceLoggingSettingsDto eventLoggingSettingsDto = new ResourceLoggingSettingsDto();
        if (eventLoggingSettings != null && (setting = eventLoggingSettings.get(LOGGING_RESOURCES_NAME)) != null) {
            ResourceLoggingSettingsDto resources;
            try {
                resources = mapper.readValue(setting.getValue(), ResourceLoggingSettingsDto.class);
            } catch (JsonProcessingException e) {
                logger.warn("Cannot deserialize event logs resource settings. Returning default settings.");
                resources = new ResourceLoggingSettingsDto();
            }
            eventLoggingSettingsDto = resources;
        }
        loggingSettingsDto.setEventLogs(eventLoggingSettingsDto);

        return loggingSettingsDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void updateLoggingSettings(LoggingSettingsDto loggingSettingsDto) {
        List<Setting> settings = settingRepository.findBySection(SettingsSection.LOGGING);
        Map<String, Map<String, Setting>> mappedSettings = mapSettingsByCategory(settings);

        // audit logging
        Setting setting;
        Map<String, Setting> auditLoggingSettings = mappedSettings.get(SettingsSectionCategory.AUDIT_LOGGING.getCode());
        if (auditLoggingSettings == null || (setting = auditLoggingSettings.get(LOGGING_AUDIT_LOG_OUTPUT_NAME)) == null) {
            setting = new Setting();
            setting.setSection(SettingsSection.LOGGING);
            setting.setCategory(SettingsSectionCategory.AUDIT_LOGGING.getCode());
            setting.setName(LOGGING_AUDIT_LOG_OUTPUT_NAME);
        }
        setting.setValue(loggingSettingsDto.getAuditLogs().getOutput().toString());
        settingRepository.save(setting);

        if (auditLoggingSettings == null || (setting = auditLoggingSettings.get(LOGGING_RESOURCES_NAME)) == null) {
            setting = new Setting();
            setting.setSection(SettingsSection.LOGGING);
            setting.setCategory(SettingsSectionCategory.AUDIT_LOGGING.getCode());
            setting.setName(LOGGING_RESOURCES_NAME);
        }
        try {
            setting.setValue(mapper.writeValueAsString(mapper.convertValue(loggingSettingsDto.getAuditLogs(), ResourceLoggingSettingsDto.class)));
            settingRepository.save(setting);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Cannot serialize audit logging resources settings: " + e.getMessage());
        }

        // event logging
        Map<String, Setting> eventLoggingSettings = mappedSettings.get(SettingsSectionCategory.EVENT_LOGGING.getCode());
        if (eventLoggingSettings == null || (setting = eventLoggingSettings.get(LOGGING_RESOURCES_NAME)) == null) {
            setting = new Setting();
            setting.setSection(SettingsSection.LOGGING);
            setting.setCategory(SettingsSectionCategory.EVENT_LOGGING.getCode());
            setting.setName(LOGGING_RESOURCES_NAME);
        }
        try {
            setting.setValue(mapper.writeValueAsString(loggingSettingsDto.getEventLogs()));
            settingRepository.save(setting);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Cannot serialize event logging resources settings: " + e.getMessage());
        }

        settingsCache.cacheSettings(SettingsSection.LOGGING, loggingSettingsDto);
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
            Map<String, Setting> categorySettings = mapping.computeIfAbsent(setting.getCategory(), k -> new HashMap<>());
            categorySettings.put(setting.getName(), setting);
        }

        return mapping;
    }
}

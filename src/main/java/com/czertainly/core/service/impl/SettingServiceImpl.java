package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.AuditLogOutput;
import com.czertainly.api.model.core.settings.*;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsDto;
import com.czertainly.api.model.core.settings.authentication.AuthenticationSettingsUpdateDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsDto;
import com.czertainly.api.model.core.settings.authentication.OAuth2ProviderSettingsUpdateDto;
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
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class SettingServiceImpl implements SettingService {
    public static final String UTILS_SERVICE_URL_NAME = "utilsServiceUrl";
    public static final String NOTIFICATIONS_MAPPING_NAME = "notificationsMapping";

    public static final String LOGGING_AUDIT_LOG_OUTPUT_NAME = "output";
    public static final String LOGGING_RESOURCES_NAME = "resources";

    public static final String AUTHENTICATION_DISABLE_LOCALHOST_NAME = "disableLocalhostUser";

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
    }

    @Scheduled(fixedRateString ="${settings.cache.refresh-interval}", timeUnit = TimeUnit.SECONDS)
    public void refreshCache() {
        settingsCache.cacheSettings(SettingsSection.PLATFORM, getPlatformSettings());
        settingsCache.cacheSettings(SettingsSection.LOGGING, getLoggingSettings());
        settingsCache.cacheSettings(SettingsSection.NOTIFICATIONS, getNotificationSettings());
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, getAuthenticationSettings(true));
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
    public AuthenticationSettingsDto getAuthenticationSettings(boolean withClientSecret) {
        AuthenticationSettingsDto authenticationSettings = new AuthenticationSettingsDto();

        List<Setting> oauth2ProviderSettings = settingRepository.findBySectionAndCategory(SettingsSection.AUTHENTICATION, SettingsSectionCategory.OAUTH2_PROVIDER.getCode());
        for (Setting oauth2Provider : oauth2ProviderSettings) {
            OAuth2ProviderSettingsDto oAuth2ProviderSettings;
            try {
                oAuth2ProviderSettings = objectMapper.readValue(oauth2Provider.getValue(), OAuth2ProviderSettingsDto.class);
                if (!withClientSecret) oAuth2ProviderSettings.setClientSecret(null);
            } catch (JsonProcessingException e) {
                throw new ValidationException("Cannot deserialize OAuth2 Provider Settings for provider '%s'.".formatted(oauth2Provider.getName()));
            }
            authenticationSettings.getOAuth2Providers().put(oauth2Provider.getName(), oAuth2ProviderSettings);
        }
        Setting disableLocalhostSetting = settingRepository.findBySectionAndCategoryAndName(SettingsSection.AUTHENTICATION, null, AUTHENTICATION_DISABLE_LOCALHOST_NAME);
        if (disableLocalhostSetting != null) {
            authenticationSettings.setDisableLocalhostUser(Boolean.parseBoolean(disableLocalhostSetting.getValue()));
        }

        return authenticationSettings;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void updateAuthenticationSettings(AuthenticationSettingsUpdateDto authenticationSettingsDto) {
        Setting disableLocalhostSetting = settingRepository.findBySectionAndCategoryAndName(SettingsSection.AUTHENTICATION, null, AUTHENTICATION_DISABLE_LOCALHOST_NAME);
        if (disableLocalhostSetting == null) {
            disableLocalhostSetting = new Setting();
            disableLocalhostSetting.setSection(SettingsSection.AUTHENTICATION);
            disableLocalhostSetting.setName(AUTHENTICATION_DISABLE_LOCALHOST_NAME);
        }
        disableLocalhostSetting.setValue(String.valueOf(authenticationSettingsDto.isDisableLocalhostUser()));
        settingRepository.save(disableLocalhostSetting);

        if (authenticationSettingsDto.getOAuth2Providers() != null) {
            settingRepository.deleteBySectionAndCategory(SettingsSection.AUTHENTICATION, SettingsSectionCategory.OAUTH2_PROVIDER.getCode());

            for (OAuth2ProviderSettingsDto providerDto : authenticationSettingsDto.getOAuth2Providers()) {
                updateOAuth2ProviderSettings(providerDto.getName(), providerDto);
            }
        }
        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, getAuthenticationSettings(true));
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.DETAIL)
    public OAuth2ProviderSettingsDto getOAuth2ProviderSettings(String providerName, boolean withClientSecret) {
        Setting setting = settingRepository.findBySectionAndCategoryAndName(SettingsSection.AUTHENTICATION, SettingsSectionCategory.OAUTH2_PROVIDER.getCode(), providerName);
        OAuth2ProviderSettingsDto settingsDto = null;
        if (setting != null) {
            try {
                settingsDto = objectMapper.readValue(setting.getValue(), OAuth2ProviderSettingsDto.class);
                if (!withClientSecret) settingsDto.setClientSecret(null);
            } catch (JsonProcessingException e) {
                throw new ValidationException("Cannot deserialize OAuth2 Provider Settings for provider '%s'.".formatted(providerName));
            }
        }
        return settingsDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void updateOAuth2ProviderSettings(String providerName, OAuth2ProviderSettingsUpdateDto settingsDto) {
        validateOAuth2ProviderSettings(settingsDto, false);
        Setting settingForRegistrationId = settingRepository.findBySectionAndCategoryAndName(SettingsSection.AUTHENTICATION, SettingsSectionCategory.OAUTH2_PROVIDER.getCode(), providerName);
        Setting setting = settingForRegistrationId == null ? new Setting() : settingForRegistrationId;
        setting.setSection(SettingsSection.AUTHENTICATION);
        setting.setCategory(SettingsSectionCategory.OAUTH2_PROVIDER.getCode());
        setting.setName(providerName);
        settingsDto.setClientSecret(SecretsUtil.encryptAndEncodeSecretString(settingsDto.getClientSecret(), SecretEncodingVersion.V1));
        try {
            OAuth2ProviderSettingsDto fullSettingsDto;
            if (settingsDto instanceof OAuth2ProviderSettingsDto s) {
                fullSettingsDto = s;
            } else {
                fullSettingsDto = objectMapper.convertValue(settingsDto, OAuth2ProviderSettingsDto.class);
                fullSettingsDto.setName(providerName);
            }
            setting.setValue(objectMapper.writeValueAsString(fullSettingsDto));
        } catch (JsonProcessingException e) {
            throw new ValidationException("Cannot serialize OAuth2 provider settings for provider '%s'.".formatted(providerName));
        }
        settingRepository.save(setting);

        settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, getAuthenticationSettings(true));
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public void removeOAuth2Provider(String providerName) {
        long deleted = settingRepository.deleteBySectionAndCategoryAndName(SettingsSection.AUTHENTICATION, SettingsSectionCategory.OAUTH2_PROVIDER.getCode(), providerName);
        if (deleted > 0) {
            settingsCache.cacheSettings(SettingsSection.AUTHENTICATION, getAuthenticationSettings(true));
        }
    }

    private Map<String, Map<String, Setting>> mapSettingsByCategory(List<Setting> settings) {
        var mapping = new HashMap<String, Map<String, Setting>>();

        for (Setting setting : settings) {
            Map<String, Setting> categorySettings = mapping.computeIfAbsent(setting.getCategory(), k -> new HashMap<>());
            categorySettings.put(setting.getName(), setting);
        }

        return mapping;
    }

    private void validateOAuth2ProviderSettings(OAuth2ProviderSettingsUpdateDto settingsDto, boolean checkAvailability) {

        if (settingsDto.getJwkSet() == null && settingsDto.getJwkSetUrl() == null)
            throw new ValidationException("Missing JWK Set URL or encoded JWK Set.");
        checkJwkSetValidity(settingsDto);
        if (checkAvailability) {
            for (String urlString : List.of(settingsDto.getJwkSetUrl(), settingsDto.getAuthorizationUrl(), settingsDto.getTokenUrl(), settingsDto.getLogoutUrl())) {
                URL url;
                try {
                    url = new URI(urlString).toURL();
                    HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                    huc.setRequestMethod("OPTIONS");
                    if (huc.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                        throw new ValidationException("URL %s is could not be reached.");
                    }
                } catch (IOException | URISyntaxException e) {
                    throw new ValidationException("Could not verify if URL %s is reachable: %s".formatted(urlString, e.getCause().toString()));
                }
            }
        }
    }

    private void checkJwkSetValidity(OAuth2ProviderSettingsUpdateDto settingsDto) {
        String jwkSet;
        if (settingsDto.getJwkSetUrl() != null) {
            try {
                URL url = new URI(settingsDto.getJwkSetUrl()).toURL();
                URLConnection urlConnection = url.openConnection();
                urlConnection.setConnectTimeout(5000);
                urlConnection.setReadTimeout(5000);
                try (InputStream stream = url.openStream()) {
                    jwkSet = new String(stream.readAllBytes());
                }
            } catch (MalformedURLException | URISyntaxException e) {
                throw new ValidationException("Unable to convert JWK Set URL to URL instance: " + e.getMessage());
            } catch (IOException e) {
                throw new ValidationException("Unable to open connection for JWK Set URL: " + e.getMessage());
            }
        } else {
            jwkSet = new String(Base64.getDecoder().decode(settingsDto.getJwkSet()));
        }
        try {
            JWKSet.parse(jwkSet);
        } catch (ParseException e) {
            throw new ValidationException("JWK Set is invalid: " + e.getMessage());
        }

    }
}

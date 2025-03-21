package com.czertainly.core.service;

import com.czertainly.api.model.core.settings.*;

import com.czertainly.api.model.core.settings.authentication.*;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;

public interface SettingService {

    /**
     * Get platform settings
     * @return platform settings
     * {@link com.czertainly.api.model.core.settings.PlatformSettingsDto}
     */
    PlatformSettingsDto getPlatformSettings();

    /**
     * Update platform settings
     * @param platformSettings Platform settings DTO
     */
    void updatePlatformSettings(PlatformSettingsDto platformSettings);

    NotificationSettingsDto getNotificationSettings();

    void updateNotificationSettings(NotificationSettingsDto notificationSettings);

    AuthenticationSettingsDto getAuthenticationSettings(boolean withClientSecret);

    void updateAuthenticationSettings(AuthenticationSettingsUpdateDto authenticationSettingsDto);

    OAuth2ProviderSettingsDto getOAuth2ProviderSettings(String providerName, boolean withClientSecret);

    void updateOAuth2ProviderSettings(String providerName, OAuth2ProviderSettingsUpdateDto providerSettings);

    void removeOAuth2Provider(String providerName);

    LoggingSettingsDto getLoggingSettings();

    void updateLoggingSettings(LoggingSettingsDto loggingSettingsDto);

}

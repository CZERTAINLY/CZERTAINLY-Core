package com.czertainly.core.service;

import com.czertainly.api.model.core.settings.*;

import java.util.List;

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

    Oauth2SettingsDto getOauth2ProviderSettings(String providerName);

    void updateOauth2ProviderSettings(Oauth2SettingsDto notificationSettingsDto);

    List<String> getListOfOauth2Clients();

    Oauth2ProviderSettings findOauth2ProviderByIssuerUri(String issuerUri);

}

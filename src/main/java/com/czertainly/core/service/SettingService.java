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

    OAuth2SettingsDto getOAuth2ProviderSettings(String providerName);

    void updateOAuth2ProviderSettings(OAuth2SettingsDto notificationSettingsDto);

    List<String> getListOfOAuth2Clients();

    OAuth2ProviderSettings findOAuth2ProviderByIssuerUri(String issuerUri);

}

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

    OAuth2ProviderSettings getOAuth2ProviderSettings(String providerName);

    void updateOAuth2ProviderSettings(String providerName, OAuth2ProviderSettings providerSettings);

    List<String> listNamesOfOAuth2Providers();

    OAuth2ProviderSettings findOAuth2ProviderByIssuerUri(String issuerUri);

    void removeOAuth2Provider(String providerName);

    List<OAuth2SettingsDto> listOAuth2Providers();

}

package com.czertainly.core.service;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.settings.*;
import com.czertainly.core.dao.entity.Notification;

import java.util.List;
import java.util.Map;

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

    List<Oauth2SettingsDto> getOauth2ProviderSettings();

    void updateOauth2ProviderSettings(List<Oauth2SettingsDto> notificationSettingsDto);

    List<String> getListOfOauth2Clients();

    Oauth2ResourceServerSettingsDto getOauth2ResourceServerSettings();
    void updateOauth2ResourceServerSettings(Oauth2ResourceServerSettingsDto oauth2ResourceServerSettingsDto);
}

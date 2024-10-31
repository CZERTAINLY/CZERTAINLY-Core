package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.core.settings.*;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.converter.SettingsSectionCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SettingControllerImpl implements SettingController {

    private SettingService settingService;

    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(SettingsSection.class, new SettingsSectionCodeConverter());
    }

    @Override
    public PlatformSettingsDto getPlatformSettings() {
        return settingService.getPlatformSettings();
    }

    @Override
    public void updatePlatformSettings(PlatformSettingsDto request) {
        settingService.updatePlatformSettings(request);
    }

    @Override
    public NotificationSettingsDto getNotificationsSettings() {
        return settingService.getNotificationSettings();
    }

    @Override
    public void updateNotificationsSettings(NotificationSettingsDto notificationSettingsDto) {
        settingService.updateNotificationSettings(notificationSettingsDto);
    }

    @Override
    public OAuth2ProviderSettings getOAuth2ProviderSettings(String providerName) {
        return settingService.getOAuth2ProviderSettings(providerName, false);
    }

    @Override
    public void updateOAuth2ProviderSettings(String providerName, OAuth2ProviderSettings oauth2SettingsDto) {
        settingService.updateOAuth2ProviderSettings(providerName, oauth2SettingsDto);
    }

    @Override
    public List<OAuth2SettingsDto> listOAuth2Providers() {
        return settingService.listOAuth2Providers();
    }

    @Override
    public void removeOAuth2Provider(String providerName) {
        settingService.removeOAuth2Provider(providerName);
    }
}

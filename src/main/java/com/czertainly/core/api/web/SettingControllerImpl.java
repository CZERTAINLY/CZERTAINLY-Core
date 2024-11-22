package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.core.settings.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.settings.logging.LoggingSettingsDto;
import com.czertainly.api.model.core.settings.NotificationSettingsDto;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.converter.SettingsSectionCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

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
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, operation = Operation.DETAIL, name = "platform")
    public PlatformSettingsDto getPlatformSettings() {
        return settingService.getPlatformSettings();
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, operation = Operation.UPDATE, name = "platform")
    public void updatePlatformSettings(PlatformSettingsDto request) {
        settingService.updatePlatformSettings(request);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, operation = Operation.DETAIL, name = "notifications")
    public NotificationSettingsDto getNotificationsSettings() {
        return settingService.getNotificationSettings();
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, operation = Operation.UPDATE, name = "notifications")
    public void updateNotificationsSettings(NotificationSettingsDto notificationSettingsDto) {
        settingService.updateNotificationSettings(notificationSettingsDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, operation = Operation.DETAIL, name = "authentication")
    public AuthenticationSettingsDto getAuthenticationSettings() {
        return settingService.getAuthenticationSettings(false);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, affiliatedResource = Resource.AUTHENTICATION_PROVIDER, operation = Operation.DETAIL, name = "authentication")
    public OAuth2ProviderSettingsDto getOAuth2ProviderSettings(@LogResource(name = true, affiliated = true) String providerName) {
        return settingService.getOAuth2ProviderSettings(providerName, false);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, affiliatedResource = Resource.AUTHENTICATION_PROVIDER, operation = Operation.UPDATE, name = "authentication")
    public void updateOAuth2ProviderSettings(@LogResource(name = true, affiliated = true) String providerName, OAuth2ProviderSettingsDto oauth2SettingsDto) {
        settingService.updateOAuth2ProviderSettings(providerName, oauth2SettingsDto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, affiliatedResource = Resource.AUTHENTICATION_PROVIDER, operation = Operation.DELETE, name = "authentication")
    public void removeOAuth2Provider(@LogResource(name = true, affiliated = true) String providerName) {
        settingService.removeOAuth2Provider(providerName);
    }
    
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, operation = Operation.DETAIL, name = "logging")
    public LoggingSettingsDto getLoggingSettings() {
        return settingService.getLoggingSettings();
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SETTINGS, operation = Operation.UPDATE, name = "logging")
    public void updateLoggingSettings(LoggingSettingsDto loggingSettingsDto) {
        settingService.updateLoggingSettings(loggingSettingsDto);
    }
}

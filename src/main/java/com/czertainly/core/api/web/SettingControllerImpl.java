package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.core.settings.NotificationSettingsDto;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
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
}

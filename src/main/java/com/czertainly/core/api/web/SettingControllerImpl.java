package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.core.setting.Section;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.converter.SectionCodeConverter;
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
        webdataBinder.registerCustomEditor(Section.class, new SectionCodeConverter());
    }

    @Override
    public Object getSetting(Section section) throws NotFoundException {
        return settingService.getSetting(section);
    }

    @Override
    public Object updateSetting(Section section, Object request) {
        return settingService.updateSetting(section, request);
    }
}

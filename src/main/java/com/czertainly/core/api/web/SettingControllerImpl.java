package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.SettingController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.setting.AllSettingsDto;
import com.czertainly.api.model.core.setting.Section;
import com.czertainly.api.model.core.setting.SectionDto;
import com.czertainly.api.model.core.setting.SectionSettingsDto;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.converter.SectionCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
    public List<SectionDto> getSections() {
        return settingService.getSections();
    }

    @Override
    public AllSettingsDto getAllSettings() {
        return settingService.getAllSettings();
    }

    @Override
    public List<SectionSettingsDto> getSettings() {
        return settingService.getSettings();
    }

    @Override
    public List<SectionSettingsDto> updateSettings(Map<Section, List<RequestAttributeDto>> attributes) {
        return settingService.updateSettings(attributes);
    }
}

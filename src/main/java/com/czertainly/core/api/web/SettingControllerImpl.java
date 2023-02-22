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
    public List<SectionDto> getSettingsSections() {
        return settingService.getSettingSections();
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
    public List<BaseAttribute> getSectionSettingsAttributes(Section section) throws NotFoundException {
        return settingService.getSectionSettingsAttributes(section);
    }

    @Override
    public SectionSettingsDto getSectionSettings(Section section) throws NotFoundException {
        return settingService.getSectionSettings(section);
    }

    @Override
    public SectionSettingsDto updateSectionSettings(Section section, List<RequestAttributeDto> attributes) {
        return settingService.updateSectionSettings(section, attributes);
    }
}

package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.setting.*;

import java.util.List;

public interface SettingService {

    /**
     * Get the list of setting sections by using the section enum
     * @return List of sections DTOs
     * {@link com.czertainly.api.model.core.setting.SectionDto}
     */
    List<SectionDto> getSettingSections();

    /**
     * Get all settings extracted from attributes in dedicated DTO
     * @return Settings DTO
     * {@link com.czertainly.api.model.core.setting.AllSettingsDto}
     */
    AllSettingsDto getAllSettings();

    /**
     * Get the list of all settings
     * @return Sections Settings DTO
     * {@link com.czertainly.api.model.core.setting.SectionSettingsDto}
     */
    List<SectionSettingsDto> getSettings();

    /**
     * Get the list of section settings in form of attributes
     * @param section Section of the settings
     * @return Deserialized attributes definitions for section settings
     * {@link com.czertainly.api.model.common.attribute.v2.BaseAttribute}
     */
    List<BaseAttribute> getSectionSettingsAttributes(Section section) throws NotFoundException;

    /**
     * Get the list of section settings in form of response attributes
     * @param section Section of the settings
     * @return Section settings DTO
     * {@link com.czertainly.api.model.core.setting.SectionSettingsDto}
     */
    SectionSettingsDto getSectionSettings(Section section);

    /**
     * Update setting section by using the section enum
     * @param section Section of the settings
     * @param attributes Request attributes with content of settings
     * @return Settings DTO
     * {@link com.czertainly.api.model.core.setting.SectionSettingsDto}
     */
    SectionSettingsDto updateSectionSettings(Section section, List<RequestAttributeDto> attributes);

}

package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.setting.*;

import java.util.List;
import java.util.Map;

public interface SettingService {

    /**
     * Get the list of setting sections by using the section enum
     * @return List of sections DTOs
     * {@link com.czertainly.api.model.core.setting.SectionDto}
     */
    List<SectionDto> getSections();

    /**
     * Get all settings extracted from attributes in dedicated DTO
     * @return Settings DTO
     * {@link com.czertainly.api.model.core.setting.AllSettingsDto}
     */
    AllSettingsDto getAllSettings();

    /**
     * Get the list of all settings
     * @return List of sections settings
     * {@link com.czertainly.api.model.core.setting.SectionSettingsDto}
     */
    List<SectionSettingsDto> getSettings();

    /**
     * Update settings per section
     * @param attributes Request attributes with content of settings mapped by section
     * @return List of sections settings
     * {@link com.czertainly.api.model.core.setting.SectionSettingsDto}
     */
    List<SectionSettingsDto> updateSettings(Map<Section, List<RequestAttributeDto>> attributes);

}

package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.setting.Section;

import java.util.List;

public interface SettingService {

    /**
     * Get the detail of a specific setting section by using the section enum
     * @param section Section of the setting
     * @return Deserialized response corresponding to the type of section. This will include one of
     * {@link com.czertainly.api.model.core.setting.UtilServiceSettingDto}
     */
    Object getSetting(Section section) throws NotFoundException;

    /**
     * Update setting section by using the section enum
     * @param section Section of the setting
     * @param request Setting DTO
     * @return Deserialized response corresponding to the type of section.
     *
     * The input and output response for the method will be one of
     * {@link com.czertainly.api.model.core.setting.UtilServiceSettingDto}
     */
    Object updateSetting(Section section, Object request);
}

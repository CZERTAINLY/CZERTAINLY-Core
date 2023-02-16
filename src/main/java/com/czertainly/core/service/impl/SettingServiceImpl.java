package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.setting.UtilServiceSettingRequestDto;
import com.czertainly.api.model.core.setting.Section;
import com.czertainly.api.model.core.setting.UtilServiceSettingDto;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.SettingRepository;
import com.czertainly.core.service.*;
import com.czertainly.core.util.SerializationUtil;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class SettingServiceImpl implements SettingService {

    private static final Logger logger = LoggerFactory.getLogger(SettingServiceImpl.class);

    private SettingRepository settingRepository;

    public static final String DEFAULT_UTIL_SERVICE_URL = "http://util-service:8080";

    @Autowired
    public void setSettingRepository(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    public Object getSetting(Section section) {
        Setting setting = settingRepository.findBySection(section)
                .orElse(null);
        if(setting != null) return setting.toDto(section);
        return initializeSetting(section);
    }

    @Override
    public Object updateSetting(Section section, Object request) {
        // Use switch to find the section type and add the data accordingly
        if (section.equals(Section.UTIL_SERVICE)) {
            processUtilServiceSetting(request);
            return getSetting(Section.UTIL_SERVICE);
        }
        return null;
    }

    private Object initializeSetting(Section section) {
        switch (section){
            case UTIL_SERVICE -> {
                UtilServiceSettingDto dto = new UtilServiceSettingDto();
                dto.setUrl(DEFAULT_UTIL_SERVICE_URL);
                dto.setName(Section.UTIL_SERVICE.getName());
                dto.setSection(Section.UTIL_SERVICE);
                dto.setUuid(UUID.randomUUID().toString());
                dto.setDescription(Section.UTIL_SERVICE.getDescription());
                return dto;
            }
        }
        return null;
    }

    private Object processUtilServiceSetting(Object request) {
        UtilServiceSettingRequestDto requestDto = SerializationUtil.convertValue(request, UtilServiceSettingRequestDto.class);
        UtilServiceSettingDto settingDto = new UtilServiceSettingDto();
        settingDto.setName(Section.UTIL_SERVICE.getName());
        settingDto.setDescription(Section.UTIL_SERVICE.getDescription());
        settingDto.setSection(Section.UTIL_SERVICE);
        settingDto.setUrl(requestDto.getUrl());
        return createSettingEntity(Section.UTIL_SERVICE.getName(), Section.UTIL_SERVICE, settingDto);
    }

    private Setting createSettingEntity(String name, Section section, Object data) {
        Setting setting = new Setting();
        setting.setName(name);
        setting.setData(data);
        setting.setSection(section);
        settingRepository.save(setting);
        return setting;
    }
}

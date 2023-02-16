package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.setting.UtilServiceSettingRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.api.model.core.setting.Section;
import com.czertainly.api.model.core.setting.UtilServiceSettingDto;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.SettingRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class SettingServiceTest extends BaseSpringBootTest {

    @Autowired
    private SettingService settingService;

    @Autowired
    private SettingRepository settingRepository;

    private Setting setting;

    @BeforeEach
    public void setUp() {
        setting = new Setting();
        setting.setName("UTIL_SERVICE");
        setting.setSection(Section.UTIL_SERVICE);
        setting.setData("{\"name\":\"UTIL_SERVICE\",\"description\":\"Sample Description\",\"url\":\"http://localhost:8080\",\"section\":\"UTIL_SERVICE\"}");
        setting = settingRepository.save(setting);
    }

    @Test
    public void createSetting() {
        UtilServiceSettingRequestDto requestDto = new UtilServiceSettingRequestDto();
        requestDto.setUrl("http://localhost:8080");

        UtilServiceSettingDto dto = (UtilServiceSettingDto) settingService.updateSetting(Section.UTIL_SERVICE, requestDto);
        Assertions.assertEquals(dto.getUrl(), requestDto.getUrl());
    }


    @Test
    public void getUtilServiceSetting() throws NotFoundException {
        UtilServiceSettingDto settingDto = (UtilServiceSettingDto) settingService.getSetting(Section.UTIL_SERVICE);
        Assertions.assertNotNull(settingDto);
        Assertions.assertEquals("http://localhost:8080", settingDto.getUrl());
    }
}

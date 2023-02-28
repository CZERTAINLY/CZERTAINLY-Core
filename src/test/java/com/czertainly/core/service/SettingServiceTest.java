package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.core.setting.AllSettingsDto;
import com.czertainly.api.model.core.setting.Section;
import com.czertainly.api.model.core.setting.SectionDto;
import com.czertainly.api.model.core.setting.SectionSettingsDto;
import com.czertainly.core.service.impl.SettingServiceImpl;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SettingServiceTest extends BaseSpringBootTest {

    @Autowired
    private SettingService settingService;

    @BeforeEach
    public void setUp() {
    }

    @Test
    public void getAllSettings() {
        AllSettingsDto allSettingsDto = settingService.getAllSettings();
        Assertions.assertNotNull(allSettingsDto.getGeneral());
    }

    @Test
    public void getSettings() {
        List<SectionDto> sections = settingService.getSections();

        List<SectionSettingsDto> sectionSettings = settingService.getSettings();
        Assertions.assertEquals(sections.size(), sectionSettings.size());
    }

    @Test
    public void updateGeneralSettings() {
        String utilsServiceUrl = "http://util-service:8080";

        List<SectionSettingsDto> attrs = settingService.getSettings();

        Optional<SectionSettingsDto> sectionSettings = attrs.stream().filter(settings -> settings.getSection().equals(Section.GENERAL)).findFirst();
        Assertions.assertEquals(true, sectionSettings.isPresent());

        Optional<BaseAttribute> urlAttr = sectionSettings.get().getAttributeDefinitions().stream().filter(attr -> attr.getName().equals(SettingServiceImpl.ATTRIBUTE_DATA_UTILS_SERVICE_URL)).findFirst();
        Assertions.assertEquals(true, urlAttr.isPresent());

        Map<Section, List<RequestAttributeDto>> request = new HashMap<>();

        RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
        requestAttributeDto.setUuid(urlAttr.get().getUuid());
        requestAttributeDto.setName(urlAttr.get().getName());
        requestAttributeDto.setContent(List.of(new StringAttributeContent(utilsServiceUrl)));
        request.put(Section.GENERAL, List.of(requestAttributeDto));

        List<SectionSettingsDto> sectionSettingsDto = settingService.updateSettings(request);
        sectionSettings = sectionSettingsDto.stream().filter(settings -> settings.getSection().equals(Section.GENERAL)).findFirst();
        Assertions.assertEquals(true, sectionSettings.isPresent());

        Optional<ResponseAttributeDto> responseUrlAttr = sectionSettings.get().getAttributes().stream().filter(attr -> attr.getName().equals(SettingServiceImpl.ATTRIBUTE_DATA_UTILS_SERVICE_URL)).findFirst();
        Assertions.assertEquals(true, responseUrlAttr.isPresent());
        Assertions.assertEquals(utilsServiceUrl, (String)responseUrlAttr.get().getContent().get(0).getData());

        AllSettingsDto allSettingsDto = settingService.getAllSettings();
        Assertions.assertEquals(utilsServiceUrl, allSettingsDto.getGeneral().getUtilsServiceUrl());
    }
}

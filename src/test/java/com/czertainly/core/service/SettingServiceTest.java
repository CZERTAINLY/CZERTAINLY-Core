package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.core.setting.AllSettingsDto;
import com.czertainly.api.model.core.setting.Section;
import com.czertainly.api.model.core.setting.SectionSettingsDto;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

public class SettingServiceTest extends BaseSpringBootTest {

    public static final String ATTRIBUTE_DATA_UTILS_SERVICE_URL = "data_utilsServiceUrl";

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
    public void getSectionSettings() throws NotFoundException {
        SectionSettingsDto dto = settingService.getSectionSettings(Section.GENERAL);
        Assertions.assertEquals(dto.getSection(), Section.GENERAL);
    }

    @Test
    public void updateGeneralSettings() throws NotFoundException {
        String utilsServiceUrl = "http://util-service:8080";

        List<BaseAttribute> attrs = settingService.getSectionSettingsAttributes(Section.GENERAL);

        Optional<BaseAttribute> urlAttr = attrs.stream().filter(attr -> attr.getName().equals(ATTRIBUTE_DATA_UTILS_SERVICE_URL)).findFirst();
        Assertions.assertEquals(true, urlAttr.isPresent());

        RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
        requestAttributeDto.setUuid(urlAttr.get().getUuid());
        requestAttributeDto.setName(urlAttr.get().getName());
        requestAttributeDto.setContent(List.of(new StringAttributeContent(utilsServiceUrl)));

        SectionSettingsDto sectionSettingsDto = settingService.updateSectionSettings(Section.GENERAL, List.of(requestAttributeDto));
        Optional<ResponseAttributeDto> responseUrlAttr = sectionSettingsDto.getAttributes().stream().filter(attr -> attr.getName().equals(ATTRIBUTE_DATA_UTILS_SERVICE_URL)).findFirst();
        Assertions.assertEquals(true, responseUrlAttr.isPresent());
        Assertions.assertEquals(utilsServiceUrl, (String)responseUrlAttr.get().getContent().get(0).getData());

        AllSettingsDto allSettingsDto = settingService.getAllSettings();
        Assertions.assertEquals(utilsServiceUrl, allSettingsDto.getGeneral().getUtilsServiceUrl());
    }
}

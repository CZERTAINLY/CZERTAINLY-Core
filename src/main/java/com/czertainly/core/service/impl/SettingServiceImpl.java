package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.setting.*;
import com.czertainly.core.dao.entity.Setting;
import com.czertainly.core.dao.repository.SettingRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class SettingServiceImpl implements SettingService {
    public static final String ATTRIBUTE_DATA_UTILS_SERVICE_URL = "data_utilsServiceUrl";
    public static final String ATTRIBUTE_DATA_UTILS_SERVICE_URL_UUID = "3e634fb2-32f4-4363-a489-2576d7d1aaf7";
    public static final String ATTRIBUTE_DATA_UTILS_SERVICE_URL_LABEL = "Utils Service API URL";
    public static final String ATTRIBUTE_DATA_UTILS_SERVICE_URL_DESCRIPTION = "URL where runs Utils Service API";

    private static final Logger logger = LoggerFactory.getLogger(SettingServiceImpl.class);

    private SettingRepository settingRepository;
    private DataAttribute utilsServiceUrl;

    @Autowired
    public void setSettingRepository(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    public List<SectionDto> getSettingSections() {
        ArrayList<SectionDto> sections = new ArrayList<>();
        for (Section section: Section.values()) {
            SectionDto sectionDto = new SectionDto();
            sectionDto.setSection(section);
            sectionDto.setName(section.getName());
            sectionDto.setDescription(section.getDescription());
            sections.add(sectionDto);
        }

        return sections;
    }

    @Override
    public AllSettingsDto getAllSettings() {
        AllSettingsDto allSettingsDto = new AllSettingsDto();
        allSettingsDto.setGeneral(getGeneralSettings());

        return allSettingsDto;
    }

    @Override
    public List<SectionSettingsDto> getSettings() {
        List<SectionSettingsDto> sectionsSettings = new ArrayList<>();

        for (Section section: Section.values()) {
            sectionsSettings.add(getSectionSettings(section));
        }

        return sectionsSettings;
    }

    @Override
    public List<BaseAttribute> getSectionSettingsAttributes(Section section) {
        switch (section) {
            case GENERAL -> {
                return getGeneralSectionAttributes();
            }
        }

        return new ArrayList<>();
    }

    @Override
    public SectionSettingsDto getSectionSettings(Section section) {
        Setting setting = settingRepository.findBySection(section)
                .orElse(null);
        if(setting == null) {
            setting = new Setting();
            setting.setSection(section);
            settingRepository.save(setting);
        }
        return constructSectionSettingsDto(setting);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SETTINGS, action = ResourceAction.UPDATE)
    public SectionSettingsDto updateSectionSettings(Section section, List<RequestAttributeDto> attributes) {
        Setting setting = settingRepository.findBySection(section)
                .orElse(null);

        if(setting == null) {
            setting = new Setting();
            setting.setSection(section);
        }

        List<DataAttribute> mergedAttributes = AttributeDefinitionUtils.mergeAttributes(getSectionSettingsAttributes(section), attributes);
        setting.setAttributes(AttributeDefinitionUtils.serialize(mergedAttributes));
        settingRepository.save(setting);

        return constructSectionSettingsDto(setting);
    }

    private SectionSettingsDto constructSectionSettingsDto(Setting setting) {
        SectionSettingsDto dto = new SectionSettingsDto();
        dto.setSection(setting.getSection());
        dto.setName(setting.getSection().getName());
        dto.setDescription(setting.getSection().getDescription());
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(AttributeDefinitionUtils.deserialize(setting.getAttributes(), DataAttribute.class)));

        return dto;
    }

    private List<BaseAttribute> getGeneralSectionAttributes() {
        List<BaseAttribute> attrs = new ArrayList<>();

        utilsServiceUrl = new DataAttribute();
        utilsServiceUrl.setUuid(ATTRIBUTE_DATA_UTILS_SERVICE_URL_UUID);
        utilsServiceUrl.setName(ATTRIBUTE_DATA_UTILS_SERVICE_URL);
        utilsServiceUrl.setDescription(ATTRIBUTE_DATA_UTILS_SERVICE_URL_DESCRIPTION);
        utilsServiceUrl.setType(AttributeType.DATA);
        utilsServiceUrl.setContentType(AttributeContentType.STRING);
        // create properties
        DataAttributeProperties attributeProperties = new DataAttributeProperties();
        attributeProperties.setLabel(ATTRIBUTE_DATA_UTILS_SERVICE_URL_LABEL);
        attributeProperties.setRequired(false);
        attributeProperties.setVisible(true);
        attributeProperties.setList(false);
        attributeProperties.setMultiSelect(false);
        utilsServiceUrl.setProperties(attributeProperties);
        attrs.add(utilsServiceUrl);

        return attrs;
    }

    private GeneralSettingsDto getGeneralSettings() {
        SectionSettingsDto generalSettings = getSectionSettings(Section.GENERAL);
        Map<String, List<BaseAttributeContent>> savedSettings = generalSettings.getAttributes().stream().collect(Collectors.toMap(attr -> attr.getName(), attr -> attr.getContent()));

        GeneralSettingsDto generalSettingsDto = new GeneralSettingsDto();
        List<BaseAttributeContent> utilsUrlContent = savedSettings.get(ATTRIBUTE_DATA_UTILS_SERVICE_URL);

        if(utilsUrlContent != null && !utilsUrlContent.isEmpty()) {
            generalSettingsDto.setUtilsServiceUrl((String)utilsUrlContent.get(0).getData());
        }

        return generalSettingsDto;
    }
}

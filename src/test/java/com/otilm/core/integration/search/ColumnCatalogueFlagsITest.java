package com.otilm.core.integration.search;

import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.otilm.api.model.common.attribute.v3.CustomAttributeV3;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.enums.FilterField;
import com.otilm.core.service.CbomExternalService;
import com.otilm.core.service.DiscoveryExternalService;
import com.otilm.core.service.SigningRecordExternalService;
import com.otilm.core.service.TimeQualityConfigurationExternalService;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code displayable} and {@code sortable} flags a published column catalogue carries. Property fields resolve
 * theirs against the JPA metamodel, so they are asserted here rather than in a plain unit test.
 */
class ColumnCatalogueFlagsITest extends BaseSpringBootTest {

    @Autowired
    private DiscoveryExternalService discoveryService;

    @Autowired
    private CbomExternalService cbomService;

    @Autowired
    private SigningRecordExternalService signingRecordService;

    @Autowired
    private TimeQualityConfigurationExternalService timeQualityConfigurationService;

    @Autowired
    private AttributeEngine attributeEngine;

    private static Optional<SearchFieldDataDto> field(List<SearchFieldDataByGroupDto> catalogue, String identifier) {
        return catalogue
                .stream()
                .flatMap(group -> group.getSearchFieldData().stream())
                .filter(item -> identifier.equals(item.getFieldIdentifier()))
                .findFirst();
    }

    private static List<SearchFieldDataDto> allFields(List<SearchFieldDataByGroupDto> catalogue) {
        return catalogue.stream().flatMap(group -> group.getSearchFieldData().stream()).toList();
    }

    @Test
    void everyPublishedFieldAnswersBothFlags() {
        // A flag left null reaches the picker as "unknown", and an absent flag is read as a no - so a field that
        // simply forgot to answer would silently disappear from the column list.
        for (SearchFieldDataDto item : allFields(discoveryService.getSearchableFieldInformationByGroup())) {
            Assertions.assertNotNull(item.getDisplayable(), item.getFieldIdentifier());
            Assertions.assertNotNull(item.getSortable(), item.getFieldIdentifier());
        }
    }

    @Test
    void anOrdinaryPropertyFieldIsBothDisplayableAndSortable() {
        SearchFieldDataDto kind = field(discoveryService.getSearchableFieldInformationByGroup(),
                FilterField.DISCOVERY_KIND.name()).orElseThrow();

        Assertions.assertEquals(true, kind.getDisplayable());
        Assertions.assertEquals(true, kind.getSortable());
    }

    @Test
    void aPropertyFieldReachedThroughAJoinIsStillSortable() {
        // Ordering across a join aggregates the joined values rather than refusing, so such a field is offered.
        SearchFieldDataDto signingProfile = field(signingRecordService.getSearchableFieldInformation(),
                FilterField.SIGNING_RECORD_SIGNING_PROFILE.name()).orElseThrow();

        Assertions.assertEquals(true, signingProfile.getSortable());
    }

    @Test
    void aNativeArrayPropertyFieldIsNotSortable() {
        // It holds many values per row, so there is no single value to order the row by. No inventory in scope
        // publishes such a field today, but the rule lives in the shared catalogue helper, so it is asserted
        // wherever one exists rather than left uncovered until an inventory grows one.
        SearchFieldDataDto ntpServers = field(timeQualityConfigurationService.getSearchableFieldInformation(),
                FilterField.TIME_QUALITY_CONFIGURATION_NTP_SERVERS.name()).orElseThrow();

        Assertions.assertEquals(true, ntpServers.getDisplayable());
        Assertions.assertEquals(false, ntpServers.getSortable());
    }

    @Test
    void anAttributeFieldIsDisplayableButNotSortable() throws Exception {
        registerCustomAttribute("catalogue-flag-probe", AttributeContentType.TEXT);

        SearchFieldDataDto attribute = field(discoveryService.getSearchableFieldInformationByGroup(),
                "catalogue-flag-probe|" + AttributeContentType.TEXT.name()).orElseThrow();
        Assertions.assertEquals(true, attribute.getDisplayable());
        Assertions.assertEquals(false, attribute.getSortable());
    }

    @Test
    void aCodeBlockAttributeIsNotOfferedAsAColumn() throws Exception {
        registerCustomAttribute("catalogue-codeblock-probe", AttributeContentType.CODEBLOCK);

        SearchFieldDataDto attribute = field(discoveryService.getSearchableFieldInformationByGroup(),
                "catalogue-codeblock-probe|" + AttributeContentType.CODEBLOCK.name()).orElseThrow();
        Assertions.assertEquals(false, attribute.getDisplayable());
    }

    @Test
    void aResourceWithoutCustomAttributesPublishesNoEmptyGroup() {
        // CBOMs and signing records carry no custom attributes; the catalogue says so by leaving the group out
        // rather than by publishing an empty one for the picker to render as a bare heading.
        for (List<SearchFieldDataByGroupDto> catalogue : List
                .of(cbomService.getSearchableFieldInformationByGroup(),
                        signingRecordService.getSearchableFieldInformation())) {
            Assertions
                    .assertTrue(catalogue
                            .stream()
                            .noneMatch(group -> group.getSearchFieldData() == null
                                    || group.getSearchFieldData().isEmpty()));
            Assertions
                    .assertTrue(catalogue
                            .stream()
                            .noneMatch(group -> group.getFilterFieldSource() == FilterFieldSource.CUSTOM));
        }
    }

    private void registerCustomAttribute(String name, AttributeContentType contentType) throws Exception {
        CustomAttributeV3 attribute = new CustomAttributeV3();
        attribute.setUuid(UUID.randomUUID().toString());
        attribute.setName(name);
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(contentType);
        CustomAttributeProperties properties = new CustomAttributeProperties();
        properties.setLabel(name);
        attribute.setProperties(properties);
        attributeEngine.updateCustomAttributeDefinition(attribute, List.of(Resource.DISCOVERY));
    }
}

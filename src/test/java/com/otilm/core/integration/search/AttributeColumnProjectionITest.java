package com.otilm.core.integration.search;

import com.otilm.api.model.client.attribute.RequestAttributeV3;
import com.otilm.api.model.client.certificate.DiscoveryResponseDto;
import com.otilm.api.model.client.certificate.SearchColumnRequestDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.discovery.DiscoveryListDto;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.otilm.api.model.common.attribute.v3.CustomAttributeV3;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.TextAttributeContentV3;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.DiscoveryExternalService;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Projection of attribute-sourced columns into a listing, exercised end to end through the discovery listing - one of
 * the seven inventories that carry configurable columns, and the cheapest of them to populate.
 */
class AttributeColumnProjectionITest extends BaseSpringBootTest {

    private static final String ENVIRONMENT = "environment";
    private static final String OWNING_TEAM = "owning-team";

    @Autowired
    private DiscoveryRepository discoveryRepository;

    @Autowired
    private DiscoveryExternalService discoveryService;

    @Autowired
    private AttributeEngine attributeEngine;

    /** Carries values for both attributes, one of them multi-valued. */
    private Discovery withValues;

    /** Carries none, and must still be returned. */
    private Discovery withoutValues;

    @BeforeEach
    void loadData() throws Exception {
        withValues = saveDiscovery("discovery-with-values");
        withoutValues = saveDiscovery("discovery-without-values");

        UUID environmentUuid = registerCustomAttribute(ENVIRONMENT, "Environment");
        UUID owningTeamUuid = registerCustomAttribute(OWNING_TEAM, "Owning team");

        attributeEngine
                .updateObjectCustomAttributesContent(Resource.DISCOVERY, withValues.getUuid(),
                        List
                                .of(attributeContent(environmentUuid, ENVIRONMENT, "production", "staging", "dr-site"),
                                        attributeContent(owningTeamUuid, OWNING_TEAM, "Platform")));
    }

    private Discovery saveDiscovery(String name) {
        Discovery discovery = new Discovery();
        discovery.setName(name);
        // The list DTO stringifies the connector reference, so it has to be set even though no
        // connector is contacted here.
        discovery.setConnectorUuid(UUID.randomUUID());
        discovery.setConnectorName("test-connector");
        discovery.setStatus(DiscoveryStatus.COMPLETED);
        discovery.setConnectorStatus(DiscoveryStatus.COMPLETED);
        discovery.setKind("test-kind");
        return discoveryRepository.save(discovery);
    }

    private UUID registerCustomAttribute(String name, String label) throws Exception {
        CustomAttributeV3 attribute = new CustomAttributeV3();
        attribute.setUuid(UUID.randomUUID().toString());
        attribute.setName(name);
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(AttributeContentType.TEXT);
        CustomAttributeProperties properties = new CustomAttributeProperties();
        properties.setLabel(label);
        properties.setList(true);
        properties.setMultiSelect(true);
        attribute.setProperties(properties);
        attributeEngine.updateCustomAttributeDefinition(attribute, List.of(Resource.DISCOVERY));
        return UUID.fromString(attribute.getUuid());
    }

    private static RequestAttributeV3 attributeContent(UUID uuid, String name, String... values) {
        RequestAttributeV3 requestAttribute = new RequestAttributeV3();
        requestAttribute.setUuid(uuid);
        requestAttribute.setName(name);
        List<BaseAttributeContentV3<?>> content = new ArrayList<>();
        for (String value : values) {
            content.add(new TextAttributeContentV3(null, value));
        }
        requestAttribute.setContent(content);
        return requestAttribute;
    }

    /** The identifier the catalogue publishes for an attribute field: its name and its content type. */
    private static String fieldIdentifier(String attributeName) {
        return attributeName + "|" + AttributeContentType.TEXT.name();
    }

    private static SearchColumnRequestDto customColumn(String attributeName) {
        return new SearchColumnRequestDto(FilterFieldSource.CUSTOM, fieldIdentifier(attributeName));
    }

    private List<DiscoveryListDto> list(List<SearchColumnRequestDto> columns) {
        SearchRequestDto request = new SearchRequestDto();
        request.setColumns(columns);
        DiscoveryResponseDto response = discoveryService.listDiscoveries(SecurityFilter.create(), request);
        return response.getDiscoveries();
    }

    private DiscoveryListDto entry(List<DiscoveryListDto> discoveries, Discovery discovery) {
        return discoveries
                .stream()
                .filter(item -> item.getUuid().equals(discovery.getUuid().toString()))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Discovery %s is not on the page".formatted(discovery.getName())));
    }

    @Test
    void requestingAnAttributeColumnReturnsItsValue() {
        List<DiscoveryListDto> discoveries = list(List.of(customColumn(OWNING_TEAM)));

        Map<String, List<BaseAttributeContentV3<?>>> custom = entry(discoveries, withValues)
                .getAttributeValues()
                .get(FilterFieldSource.CUSTOM);
        Assertions.assertEquals(1, custom.get(fieldIdentifier(OWNING_TEAM)).size());
        Assertions.assertEquals("Platform", custom.get(fieldIdentifier(OWNING_TEAM)).getFirst().getData());
    }

    @Test
    void valuesAreKeyedBySourceThenIdentifier() {
        List<DiscoveryListDto> discoveries = list(List.of(customColumn(OWNING_TEAM), customColumn(ENVIRONMENT)));

        Map<FilterFieldSource, Map<String, List<BaseAttributeContentV3<?>>>> values = entry(discoveries, withValues)
                .getAttributeValues();
        Assertions.assertEquals(Set.of(FilterFieldSource.CUSTOM), values.keySet());
        Assertions
                .assertEquals(Set.of(fieldIdentifier(OWNING_TEAM), fieldIdentifier(ENVIRONMENT)),
                        values.get(FilterFieldSource.CUSTOM).keySet());
    }

    @Test
    void aMultiValuedAttributeKeepsTheOrderItWasStoredIn() {
        List<DiscoveryListDto> discoveries = list(List.of(customColumn(ENVIRONMENT)));

        List<BaseAttributeContentV3<?>> values = entry(discoveries, withValues)
                .getAttributeValues()
                .get(FilterFieldSource.CUSTOM)
                .get(fieldIdentifier(ENVIRONMENT));
        Assertions
                .assertEquals(List.of("production", "staging", "dr-site"),
                        values.stream().map(value -> (Object) value.getData()).toList());
    }

    @Test
    void anObjectWithoutAValueIsStillReturned() {
        List<DiscoveryListDto> discoveries = list(List.of(customColumn(ENVIRONMENT)));

        // The point of the assertion: absence omits the entry, it does not drop the row from the page.
        Assertions.assertNull(entry(discoveries, withoutValues).getAttributeValues());
    }

    @Test
    void aRequestedColumnTheObjectHasNoValueForIsAbsentRatherThanEmpty() {
        Discovery partial = saveDiscovery("discovery-partial");
        List<DiscoveryListDto> discoveries = list(List.of(customColumn(ENVIRONMENT), customColumn(OWNING_TEAM)));

        Assertions.assertNull(entry(discoveries, partial).getAttributeValues());
    }

    @Test
    void aRequestWithoutColumnsProjectsNothing() {
        List<DiscoveryListDto> discoveries = list(null);

        Assertions.assertTrue(discoveries.stream().allMatch(item -> item.getAttributeValues() == null));
    }

    @Test
    void anEmptyColumnListProjectsNothing() {
        List<DiscoveryListDto> discoveries = list(List.of());

        Assertions.assertTrue(discoveries.stream().allMatch(item -> item.getAttributeValues() == null));
    }

    @Test
    void aPropertyColumnProjectsNothing() {
        // Property values are already on the entry; only attribute-sourced columns need loading.
        List<DiscoveryListDto> discoveries = list(
                List.of(new SearchColumnRequestDto(FilterFieldSource.PROPERTY, "DISCOVERY_NAME")));

        Assertions.assertTrue(discoveries.stream().allMatch(item -> item.getAttributeValues() == null));
    }

    @Test
    void anUnknownAttributeColumnLeavesTheListingIntact() {
        // A saved view naming an attribute that has since been deleted must not fail the listing that carries it.
        List<DiscoveryListDto> discoveries = list(List.of(customColumn("attribute-that-no-longer-exists")));

        Assertions.assertFalse(discoveries.isEmpty());
        Assertions.assertTrue(discoveries.stream().allMatch(item -> item.getAttributeValues() == null));
    }

    @Test
    void anIdentifierWithoutAContentTypeIsIgnored() {
        List<DiscoveryListDto> discoveries = list(
                List.of(new SearchColumnRequestDto(FilterFieldSource.CUSTOM, ENVIRONMENT)));

        Assertions.assertFalse(discoveries.isEmpty());
        Assertions.assertTrue(discoveries.stream().allMatch(item -> item.getAttributeValues() == null));
    }

    @Test
    void anAttributeOfAnotherSourceIsNotReturnedForACustomColumn() {
        // The same name may exist under another attribute type; a column addresses one source only.
        List<DiscoveryListDto> discoveries = list(
                List.of(new SearchColumnRequestDto(FilterFieldSource.META, fieldIdentifier(ENVIRONMENT))));

        Assertions.assertTrue(discoveries.stream().allMatch(item -> item.getAttributeValues() == null));
    }
}

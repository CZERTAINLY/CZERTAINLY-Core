package com.otilm.core.integration.search;

import com.otilm.api.model.client.attribute.RequestAttributeV3;
import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.otilm.api.model.common.attribute.v3.CustomAttributeV3;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.TextAttributeContentV3;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.api.model.core.search.FilterConditionOperator;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.dao.entity.AttributeContentItem;
import com.otilm.core.dao.entity.AttributeDefinition;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.AttributeContentItemRepository;
import com.otilm.core.dao.repository.AttributeDefinitionRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.DiscoveryExternalService;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.otilm.core.util.builders.SearchFilterRequestDtoBuilder.aCustomAttributeFilter;

/**
 * Filtering an attribute reads its content, so it is gated like the projection that renders one and the ordering that
 * reads one for a sort key. A caller who may not read a value must not be able to ask questions about it either: the
 * rows an attribute filter returns are the rows matching the predicate, so an ungated filter answers direct questions
 * about content the same response would withhold.
 */
class AttributeFilterAuthorizationITest extends BaseSpringBootTest {

    private static final String ENVIRONMENT = "environment";

    @Autowired
    private DiscoveryExternalService discoveryService;

    @Autowired
    private DiscoveryRepository discoveryRepository;

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;

    @Autowired
    private AttributeContentItemRepository attributeContentItemRepository;

    private UUID definitionUuid;

    @BeforeEach
    void loadData() throws Exception {
        definitionUuid = registerCustomAttribute();

        seedDiscovery("first-created", "charlie");
        seedDiscovery("second-created", "alpha");
        seedDiscovery("third-created", "bravo");
    }

    @Test
    void anAttributeTheCallerMayNotReadCannotNarrowTheListing() {
        restrictObjectAccess(Resource.ATTRIBUTE, ResourceAction.MEMBERS);

        Assertions.assertEquals(List.of(), listNamesFilteredByEnvironment("alpha"));
    }

    /** The values do narrow the listing when the caller may read them, which is what the case above removes. */
    @Test
    void anAttributeTheCallerMayReadNarrowsTheListing() {
        Assertions.assertEquals(List.of("second-created"), listNamesFilteredByEnvironment("alpha"));
    }

    /**
     * Whether an object carries a value at all is content too: the projection returns nothing for a restricted
     * attribute, so a presence filter that still answered would say which objects have one.
     */
    @Test
    void aRestrictedCallerIsNotToldWhichObjectsCarryAValue() {
        restrictObjectAccess(Resource.ATTRIBUTE, ResourceAction.MEMBERS);

        Assertions.assertEquals(List.of(), listNamesFiltered(FilterConditionOperator.NOT_EMPTY, null));
    }

    /**
     * The flip side of matching nothing: a negated condition is a {@code NOT EXISTS}, so with no readable content it
     * holds for every row. That keeps rows from being dropped by a restriction the caller cannot see, and tells them
     * nothing - every object answers the same.
     */
    @Test
    void aNegatedConditionKeepsEveryRowForARestrictedCaller() {
        restrictObjectAccess(Resource.ATTRIBUTE, ResourceAction.MEMBERS);

        Assertions
                .assertEquals(List.of("first-created", "second-created", "third-created"),
                        listNamesFiltered(FilterConditionOperator.NOT_EQUALS, "alpha").stream().sorted().toList());
    }

    /** A disabled definition is one the platform has withdrawn; the projection skips its content, so a filter must. */
    @Test
    void contentOfADisabledCustomDefinitionCannotNarrowTheListing() {
        AttributeDefinition definition = attributeDefinitionRepository
                .findByAttributeUuid(definitionUuid)
                .orElseThrow();
        definition.setEnabled(false);
        attributeDefinitionRepository.saveAndFlush(definition);

        Assertions.assertEquals(List.of(), listNamesFilteredByEnvironment("alpha"));
    }

    /** Ciphertext is readable only by its own decryption path, which a listing query does not take. */
    @Test
    void encryptedContentCannotNarrowTheListingByValue() {
        encryptStoredContent("alpha");

        Assertions.assertEquals(List.of(), listNamesFilteredByEnvironment("alpha"));
    }

    /**
     * Presence is the one question an encrypted attribute is meant to answer - {@code SearchHelper} narrows an
     * encrypted field to {@code EMPTY} and {@code NOT_EMPTY} alone - so the ciphertext gate must not reach it, or the
     * catalogue would offer two operators that always answer "no value".
     */
    @Test
    void encryptedContentStillAnswersAPresenceFilter() {
        encryptStoredContent("alpha");

        Assertions
                .assertEquals(List.of("first-created", "second-created", "third-created"),
                        listNamesFiltered(FilterConditionOperator.NOT_EMPTY, null).stream().sorted().toList());
    }

    /** Moves one stored value to the encrypted column, which is where content of an encrypted attribute lives. */
    private void encryptStoredContent(String value) {
        AttributeDefinition definition = attributeDefinitionRepository
                .findByAttributeUuid(definitionUuid)
                .orElseThrow();
        List<AttributeContentItem> items = attributeContentItemRepository
                .findByAttributeDefinitionUuid(definition.getUuid());
        AttributeContentItem stored = items
                .stream()
                .filter(item -> item.getJson() != null && item.getJson().getData().toString().equals(value))
                .findFirst()
                .orElseThrow();
        stored.setEncryptedData("ciphertext");
        attributeContentItemRepository.saveAndFlush(stored);
    }

    private List<String> listNamesFilteredByEnvironment(String value) {
        return listNamesFiltered(FilterConditionOperator.EQUALS, value);
    }

    private List<String> listNamesFiltered(FilterConditionOperator condition, String value) {
        SearchFilterRequestDto filter = aCustomAttributeFilter(ENVIRONMENT, AttributeContentType.TEXT, condition,
                value);

        SearchRequestDto request = new SearchRequestDto();
        request.setPageNumber(1);
        request.setItemsPerPage(10);
        request.setFilters(List.of(filter));

        return discoveryService
                .listDiscoveries(SecurityFilter.create(), request)
                .getDiscoveries()
                .stream()
                .map(discovery -> discovery.getName())
                .toList();
    }

    private UUID registerCustomAttribute() throws Exception {
        CustomAttributeV3 attribute = new CustomAttributeV3();
        UUID uuid = UUID.randomUUID();
        attribute.setUuid(uuid.toString());
        attribute.setName(ENVIRONMENT);
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(AttributeContentType.TEXT);
        CustomAttributeProperties properties = new CustomAttributeProperties();
        properties.setLabel(ENVIRONMENT);
        properties.setVisible(true);
        attribute.setProperties(properties);
        attributeEngine.updateCustomAttributeDefinition(attribute, List.of(Resource.DISCOVERY));
        return uuid;
    }

    private void seedDiscovery(String name, String environmentValue) throws Exception {
        Discovery discovery = new Discovery();
        discovery.setName(name);
        discovery.setStatus(DiscoveryStatus.COMPLETED);
        discovery.setConnectorStatus(DiscoveryStatus.COMPLETED);
        // The list DTO reads both, so a discovery without them cannot be mapped for the response.
        discovery.setConnectorUuid(UUID.randomUUID());
        discovery.setConnectorName("attribute-filter-connector");
        discovery = discoveryRepository.save(discovery);

        attributeEngine
                .updateObjectCustomAttributesContent(Resource.DISCOVERY, discovery.getUuid(),
                        List.of(customContent(environmentValue)));
    }

    private RequestAttributeV3 customContent(String value) {
        RequestAttributeV3 requestAttribute = new RequestAttributeV3();
        requestAttribute.setUuid(definitionUuid);
        requestAttribute.setName(ENVIRONMENT);
        List<BaseAttributeContentV3<?>> content = new ArrayList<>();
        content.add(new TextAttributeContentV3(null, value));
        requestAttribute.setContent(content);
        return requestAttribute;
    }
}

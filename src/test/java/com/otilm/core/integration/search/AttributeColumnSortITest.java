package com.otilm.core.integration.search;

import com.otilm.api.model.client.attribute.RequestAttributeV3;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.certificate.SearchSortRequestDto;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.otilm.api.model.common.attribute.v3.CustomAttributeV3;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.TextAttributeContentV3;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SortDirection;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.DiscoveryRepository;
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

/**
 * Ordering by an attribute-sourced column.
 *
 * <p>
 * Filtering an attribute is an order-agnostic {@code EXISTS} subquery, so it yields nothing to order by. These cases
 * cover the correlated scalar subquery that does: that a listing orders by the stored value rather than by the raw
 * document, that an object holding no value is kept rather than dropped, and that a multi-valued attribute resolves to
 * one key instead of multiplying the row.
 */
class AttributeColumnSortITest extends BaseSpringBootTest {

    private static final String ENVIRONMENT = "environment";

    @Autowired
    private DiscoveryExternalService discoveryService;

    @Autowired
    private DiscoveryRepository discoveryRepository;

    @Autowired
    private AttributeEngine attributeEngine;

    private UUID definitionUuid;

    @BeforeEach
    void loadData() throws Exception {
        definitionUuid = registerCustomAttribute();

        // Seeded so the attribute order is neither the creation order nor the name order: a sort that never reached
        // the query, or one that ordered by the wrong column, cannot produce the expected sequence by accident.
        seedDiscovery("first-created", "charlie");
        seedDiscovery("second-created", "alpha");
        seedDiscovery("third-created", "bravo");
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

    private Discovery seedDiscovery(String name, String... environmentValues) throws Exception {
        Discovery discovery = new Discovery();
        discovery.setName(name);
        discovery.setStatus(DiscoveryStatus.COMPLETED);
        discovery.setConnectorStatus(DiscoveryStatus.COMPLETED);
        // The list DTO reads both, so a discovery without them cannot be mapped for the response.
        discovery.setConnectorUuid(UUID.randomUUID());
        discovery.setConnectorName("attribute-sort-connector");
        discovery = discoveryRepository.save(discovery);

        if (environmentValues.length > 0) {
            attributeEngine
                    .updateObjectCustomAttributesContent(Resource.DISCOVERY, discovery.getUuid(),
                            List.of(customContent(environmentValues)));
        }
        return discovery;
    }

    private RequestAttributeV3 customContent(String... values) {
        RequestAttributeV3 requestAttribute = new RequestAttributeV3();
        requestAttribute.setUuid(definitionUuid);
        requestAttribute.setName(ENVIRONMENT);
        List<BaseAttributeContentV3<?>> content = new ArrayList<>();
        for (String value : values) {
            content.add(new TextAttributeContentV3(null, value));
        }
        requestAttribute.setContent(content);
        return requestAttribute;
    }

    private List<String> listNames(SortDirection direction, int pageNumber, int itemsPerPage) {
        SearchRequestDto request = new SearchRequestDto();
        request.setPageNumber(pageNumber);
        request.setItemsPerPage(itemsPerPage);
        request
                .setSort(new SearchSortRequestDto(FilterFieldSource.CUSTOM,
                        ENVIRONMENT + "|" + AttributeContentType.TEXT.name(), direction));

        return discoveryService
                .listDiscoveries(SecurityFilter.create(), request)
                .getDiscoveries()
                .stream()
                .map(discovery -> discovery.getName())
                .toList();
    }

    @Test
    void ordersByTheAttributeValueAscending() {
        Assertions
                .assertEquals(List.of("second-created", "third-created", "first-created"),
                        listNames(SortDirection.ASC, 1, 10));
    }

    @Test
    void ordersByTheAttributeValueDescending() {
        Assertions
                .assertEquals(List.of("first-created", "third-created", "second-created"),
                        listNames(SortDirection.DESC, 1, 10));
    }

    @Test
    void orderingSpansTheWholeResultSetAcrossAPageBoundary() {
        List<String> paged = new ArrayList<>();
        paged.addAll(listNames(SortDirection.ASC, 1, 2));
        paged.addAll(listNames(SortDirection.ASC, 2, 2));

        Assertions.assertEquals(List.of("second-created", "third-created", "first-created"), paged);
    }

    /**
     * An object with no value for the sorted attribute yields a null key. It has to be kept and ordered consistently:
     * last in both directions, so reversing the sort does not lead the page with the rows the column cannot show.
     */
    @Test
    void anObjectWithoutAValueSortsLastInBothDirections() throws Exception {
        seedDiscovery("no-value");

        Assertions
                .assertEquals(List.of("second-created", "third-created", "first-created", "no-value"),
                        listNames(SortDirection.ASC, 1, 10));
        Assertions
                .assertEquals(List.of("first-created", "third-created", "second-created", "no-value"),
                        listNames(SortDirection.DESC, 1, 10));
    }

    /**
     * A multi-valued attribute has as many values as the object stores, which would leave the key ambiguous. It
     * resolves to the value at the lowest {@code item_order} - the one the cell shows first - and the row appears once
     * rather than once per value.
     */
    @Test
    void aMultiValuedAttributeSortsOnItsFirstStoredValue() throws Exception {
        seedDiscovery("multi", "aaa", "zzz");

        Assertions
                .assertEquals(List.of("multi", "second-created", "third-created", "first-created"),
                        listNames(SortDirection.ASC, 1, 10));
    }
}

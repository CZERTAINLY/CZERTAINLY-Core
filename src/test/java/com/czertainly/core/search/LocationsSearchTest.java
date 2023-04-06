package com.czertainly.core.search;

import com.czertainly.api.model.client.certificate.LocationsResponseDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchGroup;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.LocationService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LocationsSearchTest extends BaseSpringBootTest {

    @Autowired
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;
    @Autowired
    private AttributeContentRepository attributeContentRepository;
    @Autowired
    private AttributeContent2ObjectRepository attributeContent2ObjectRepository;
    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationService locationService;

    private EntityInstanceReference entityInstanceReference;

    private Connector connector;

    private Location location;

    private boolean isLoadedData = false;

    @BeforeEach
    public void loadData() {

        if (isLoadedData) {
            return;
        }

        connector = new Connector();
        connector.setName("testProviderConnector");
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        entityInstanceReference = new EntityInstanceReference();
        entityInstanceReference.setConnector(connector);
        entityInstanceReference.setConnectorName("testConnector1");
        entityInstanceReference.setCreated(LocalDateTime.now());
        entityInstanceReference.setKind("test-kind1");
        entityInstanceReference.setName("entity-ref-1");
        entityInstanceReference = entityInstanceReferenceRepository.save(entityInstanceReference);

        location = new Location();
        location.setEntityInstanceReference(entityInstanceReference);
        location.setEntityInstanceName("test-instance-name-1");
        location.setName("location1");
        location.setSupportMultipleEntries(true);
        location.setSupportKeyManagement(false);
        location.setEnabled(true);
        locationRepository.save(location);

        loadMetaData();
        loadCustomAttributesData();

        entityInstanceReference = new EntityInstanceReference();
        entityInstanceReference.setConnector(connector);
        entityInstanceReference.setConnectorName("testConnector2");
        entityInstanceReference.setCreated(LocalDateTime.now());
        entityInstanceReference.setKind("test-kind2");
        entityInstanceReference.setName("entity-ref-2");
        entityInstanceReference = entityInstanceReferenceRepository.save(entityInstanceReference);

        final Location location2 = new Location();
        location2.setEntityInstanceReference(entityInstanceReference);
        location2.setEntityInstanceName("test-instance-name-1");
        location2.setName("location1");
        location2.setSupportMultipleEntries(true);
        location2.setSupportKeyManagement(true);
        location2.setEnabled(false);
        locationRepository.save(location2);

        entityInstanceReference = new EntityInstanceReference();
        entityInstanceReference.setConnector(connector);
        entityInstanceReference.setConnectorName("testConnector3");
        entityInstanceReference.setCreated(LocalDateTime.now());
        entityInstanceReference.setKind("test3-kind");
        entityInstanceReference.setName("entity3-ref");
        entityInstanceReference = entityInstanceReferenceRepository.save(entityInstanceReference);

        final Location location3 = new Location();
        location3.setEntityInstanceReference(entityInstanceReference);
        location3.setEntityInstanceName("test-instance-name-3");
        location3.setName("location3");
        location3.setSupportMultipleEntries(false);
        location3.setSupportKeyManagement(true);
        location3.setEnabled(false);
        locationRepository.save(location3);

        isLoadedData = true;
    }

    @Test
    public void testInsertedData() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(3, responseDto.getLocations().size());
    }

    @Test
    public void testLocationByName() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY.getLabel(), SearchableFields.NAME.name(), SearchCondition.EQUALS, "location1"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(2, responseDto.getLocations().size());
    }

    @Test
    public void testLocationByInstanceName() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY.getLabel(), SearchableFields.ENTITY_INSTANCE_NAME.name(), SearchCondition.ENDS_WITH, "instance-name-3"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testLocationByEnabled() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY.getLabel(), SearchableFields.ENABLED.name(), SearchCondition.EQUALS, true));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testLocationBySupportMultipleEntries() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY.getLabel(), SearchableFields.SUPPORT_MULTIPLE_ENTRIES.name(), SearchCondition.NOT_EQUALS, true));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testLocationBySupportKeyManagement() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY.getLabel(), SearchableFields.SUPPORT_KEY_MANAGEMENT.name(), SearchCondition.EQUALS, false));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testFilterDataByMetadata() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.META.getLabel(), "attributeMeta1|TEXT", SearchCondition.CONTAINS, "-meta-"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testFilterDataByCustomAttr() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.CUSTOM.getLabel(), "attributeCustom1|TEXT", SearchCondition.CONTAINS, "-custom-"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    private LocationsResponseDto retrieveLocationsBySearch(final List<SearchFilterRequestDto> filters) {
        final SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(filters);
        return locationService.listLocations(SecurityFilter.create(), searchRequestDto);
    }

    private void loadMetaData() {
        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setContentType(AttributeContentType.TEXT);
        attributeDefinition.setCreated(LocalDateTime.now());
        attributeDefinition.setAttributeName("attributeMeta1");
        attributeDefinition.setType(AttributeType.META);
        attributeDefinition.setConnector(connector);
        attributeDefinition = attributeDefinitionRepository.save(attributeDefinition);

        final AttributeContentItem attributeContentItem = new AttributeContentItem();
        attributeContentItem.setJson(new BaseAttributeContent("reference-test-1", "data-meta-test-1"));
        AttributeContent attributeContent = new AttributeContent();
        attributeContent.setAttributeContentItems(List.of(attributeContentItem));
        attributeContent.setAttributeDefinition(attributeDefinition);
        attributeContentItem.setAttributeContent(attributeContent);
        attributeContent = attributeContentRepository.save(attributeContent);

        final AttributeContent2Object ac2o = new AttributeContent2Object();
        ac2o.setAttributeContent(attributeContent);
        ac2o.setConnector(connector);
        ac2o.setObjectUuid(location.getUuid());
        ac2o.setObjectType(Resource.LOCATION);
        attributeContent2ObjectRepository.save(ac2o);
    }

    private void loadCustomAttributesData() {
        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setContentType(AttributeContentType.TEXT);
        attributeDefinition.setCreated(LocalDateTime.now());
        attributeDefinition.setAttributeName("attributeCustom1");
        attributeDefinition.setType(AttributeType.CUSTOM);
        attributeDefinition.setConnector(connector);
        attributeDefinition = attributeDefinitionRepository.save(attributeDefinition);

        final AttributeContentItem attributeContentItem = new AttributeContentItem();
        attributeContentItem.setJson(new BaseAttributeContent("reference-test-1", "data-custom-test-1"));
        AttributeContent attributeContent = new AttributeContent();
        attributeContent.setAttributeContentItems(List.of(attributeContentItem));
        attributeContent.setAttributeDefinition(attributeDefinition);
        attributeContentItem.setAttributeContent(attributeContent);
        attributeContent = attributeContentRepository.save(attributeContent);

        final AttributeContent2Object ac2o = new AttributeContent2Object();
        ac2o.setAttributeContent(attributeContent);
        ac2o.setConnector(connector);
        ac2o.setObjectUuid(location.getUuid());
        ac2o.setObjectType(Resource.LOCATION);
        attributeContent2ObjectRepository.save(ac2o);
    }


}

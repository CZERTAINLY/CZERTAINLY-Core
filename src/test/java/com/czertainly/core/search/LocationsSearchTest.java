package com.czertainly.core.search;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.LocationsResponseDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.TextAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.EntityInstanceReference;
import com.czertainly.core.dao.entity.Location;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.LocationRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.LocationService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocationsSearchTest extends BaseSpringBootTest {

    @Autowired
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private LocationService locationService;

    private EntityInstanceReference entityInstanceReference;

    private Connector connector;

    private Location location;

    private boolean isLoadedData = false;

    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @BeforeEach
    public void loadData() throws AttributeException, NotFoundException {

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
        entityInstanceReference.setCreated(OffsetDateTime.now());
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
        entityInstanceReference.setCreated(OffsetDateTime.now());
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
        entityInstanceReference.setCreated(OffsetDateTime.now());
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
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, FilterField.LOCATION_NAME.name(), FilterConditionOperator.EQUALS, "location1"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(2, responseDto.getLocations().size());
    }

    @Test
    public void testLocationByInstanceName() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, FilterField.LOCATION_ENTITY_INSTANCE.name(), FilterConditionOperator.ENDS_WITH, "instance-name-3"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testLocationByEnabled() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, FilterField.LOCATION_ENABLED.name(), FilterConditionOperator.EQUALS, true));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testLocationBySupportMultipleEntries() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, FilterField.LOCATION_SUPPORT_MULTIPLE_ENTRIES.name(), FilterConditionOperator.NOT_EQUALS, true));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testLocationBySupportKeyManagement() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, FilterField.LOCATION_SUPPORT_KEY_MANAGEMENT.name(), FilterConditionOperator.EQUALS, false));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testFilterDataByMetadata() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.META, "attributeMeta1|TEXT", FilterConditionOperator.CONTAINS, "-meta-"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    @Test
    public void testFilterDataByCustomAttr() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.CUSTOM, "attributeCustom1|TEXT", FilterConditionOperator.CONTAINS, "-custom-"));
        final LocationsResponseDto responseDto = retrieveLocationsBySearch(filters);
        Assertions.assertEquals(1, responseDto.getLocations().size());
    }

    private LocationsResponseDto retrieveLocationsBySearch(final List<SearchFilterRequestDto> filters) {
        final SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(filters);
        return locationService.listLocations(SecurityFilter.create(), searchRequestDto);
    }

    private void loadMetaData() throws AttributeException {
        MetadataAttribute metadataAttribute = new MetadataAttribute();
        metadataAttribute.setUuid(UUID.randomUUID().toString());
        metadataAttribute.setName("attributeMeta1");
        metadataAttribute.setType(AttributeType.META);
        metadataAttribute.setContentType(AttributeContentType.TEXT);
        metadataAttribute.setProperties(new MetadataAttributeProperties() {{ setLabel("Test meta"); }});
        metadataAttribute.setContent(List.of(new TextAttributeContent("reference-test-1", "data-meta-test-1")));

        attributeEngine.updateMetadataAttribute(metadataAttribute, new ObjectAttributeContentInfo(connector.getUuid(), Resource.LOCATION, location.getUuid()));
    }

    private void loadCustomAttributesData() throws AttributeException, NotFoundException {
        CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setUuid(UUID.randomUUID().toString());
        customAttribute.setName("attributeCustom1");
        customAttribute.setType(AttributeType.CUSTOM);
        customAttribute.setContentType(AttributeContentType.TEXT);
        customAttribute.setProperties(new CustomAttributeProperties() {{ setLabel("Test custom"); }});

        List<BaseAttributeContent> contentItems = List.of(new BaseAttributeContent("reference-test-1", "data-custom-test-1"));
        RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
        requestAttributeDto.setUuid(customAttribute.getUuid());
        requestAttributeDto.setName(customAttribute.getName());
        requestAttributeDto.setContent(contentItems);

        attributeEngine.updateCustomAttributeDefinition(customAttribute, List.of(Resource.LOCATION));
        attributeEngine.updateObjectCustomAttributesContent(Resource.LOCATION, location.getUuid(), List.of(requestAttributeDto));
    }
}

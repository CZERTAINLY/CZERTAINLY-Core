package com.czertainly.core.search;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
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
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiscoveryHistorySearchTest extends BaseSpringBootTest {

    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;
    @Autowired
    private DiscoveryService discoveryService;
    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private boolean isLoadedData = false;

    private Connector connector;
    private DiscoveryHistory discoveryHistory;

    @BeforeEach
    public void loadData() {
        try {
            if (isLoadedData) {
                return;
            }

            connector = new Connector();
            connector.setName("discoveryProviderConnector");
            connector.setUrl("http://localhost:3665");
            connector.setStatus(ConnectorStatus.CONNECTED);
            connector = connectorRepository.save(connector);

            FunctionGroup functionGroup = new FunctionGroup();
            functionGroup.setCode(FunctionGroupCode.DISCOVERY_PROVIDER);
            functionGroup.setName(FunctionGroupCode.DISCOVERY_PROVIDER.getCode());
            functionGroupRepository.save(functionGroup);

            Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
            c2fg.setConnector(connector);
            c2fg.setFunctionGroup(functionGroup);
            c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("IpAndPort")));
            connector2FunctionGroupRepository.save(c2fg);

            connector.getFunctionGroups().add(c2fg);
            connectorRepository.save(connector);

            final DiscoveryHistory discovery1 = new DiscoveryHistory();
            discovery1.setName("test_discovery1");
            discovery1.setConnectorUuid(connector.getUuid());
            discovery1.setConnectorName("connector1");
            discovery1.setStatus(DiscoveryStatus.FAILED);
            discovery1.setConnectorStatus(DiscoveryStatus.FAILED);
            discovery1.setKind("kindTEST1");
            discovery1.setStartTime(DATE_FORMAT.parse("2020-01-01T10:10:10"));
            discovery1.setEndTime(DATE_FORMAT.parse("2020-02-01T10:10:10"));
            discovery1.setTotalCertificatesDiscovered(15);
            discovery1.setConnectorTotalCertificatesDiscovered(15);
            discoveryHistory = discoveryRepository.save(discovery1);

            final DiscoveryHistory discovery2 = new DiscoveryHistory();
            discovery2.setName("test_discovery2");
            discovery2.setConnectorUuid(connector.getUuid());
            discovery2.setConnectorName("connector1");
            discovery2.setStatus(DiscoveryStatus.COMPLETED);
            discovery2.setConnectorStatus(DiscoveryStatus.COMPLETED);
            discovery2.setKind("kindTEST3");
            discovery2.setStartTime(DATE_FORMAT.parse("2020-05-05T10:10:10"));
            discovery2.setEndTime(DATE_FORMAT.parse("2021-02-01T10:10:10"));
            discovery2.setTotalCertificatesDiscovered(11);
            discovery2.setConnectorTotalCertificatesDiscovered(11);
            discoveryRepository.save(discovery2);

            final DiscoveryHistory discovery3 = new DiscoveryHistory();
            discovery3.setName("test_discovery3");
            discovery3.setConnectorUuid(connector.getUuid());
            discovery3.setConnectorName("connector5");
            discovery3.setStatus(DiscoveryStatus.COMPLETED);
            discovery3.setConnectorStatus(DiscoveryStatus.COMPLETED);
            discovery3.setKind("kindTEST3");
            discovery3.setStartTime(DATE_FORMAT.parse("2022-10-01T10:10:10"));
            discovery3.setEndTime(DATE_FORMAT.parse("2023-02-01T10:10:10"));
            discovery3.setTotalCertificatesDiscovered(20);
            discovery3.setConnectorTotalCertificatesDiscovered(20);
            discoveryRepository.save(discovery3);

            final DiscoveryHistory discovery4 = new DiscoveryHistory();
            discovery4.setName("test_discovery4");
            discovery4.setConnectorUuid(connector.getUuid());
            discovery4.setConnectorName("connector1");
            discovery4.setStatus(DiscoveryStatus.IN_PROGRESS);
            discovery4.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
            discovery4.setKind("kindTEST4");
            discovery4.setStartTime(DATE_FORMAT.parse("2020-06-01T10:10:10"));
            discovery4.setEndTime(DATE_FORMAT.parse("2020-10-01T10:10:10"));
            discovery4.setTotalCertificatesDiscovered(5);
            discovery4.setConnectorTotalCertificatesDiscovered(5);
            discoveryRepository.save(discovery4);

            loadMetaData();
            loadCustomAttributesData();
            isLoadedData = true;
        } catch (ParseException | NotFoundException | AttributeException e) {
            isLoadedData = false;
        }
    }

    private void loadMetaData() throws AttributeException {
        MetadataAttribute metadataAttribute = new MetadataAttribute();
        metadataAttribute.setUuid(UUID.randomUUID().toString());
        metadataAttribute.setName("attributeMeta1");
        metadataAttribute.setType(AttributeType.META);
        metadataAttribute.setContentType(AttributeContentType.TEXT);
        metadataAttribute.setProperties(new MetadataAttributeProperties() {{ setLabel("Test meta"); }});
        metadataAttribute.setContent(List.of(new TextAttributeContent("reference-test-1", "data-meta-test-1")));

        attributeEngine.updateMetadataAttribute(metadataAttribute, new ObjectAttributeContentInfo(connector.getUuid(), Resource.DISCOVERY, discoveryHistory.getUuid()));
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

        attributeEngine.updateCustomAttributeDefinition(customAttribute, List.of(Resource.DISCOVERY));
        attributeEngine.updateObjectCustomAttributesContent(Resource.DISCOVERY, discoveryHistory.getUuid(), List.of(requestAttributeDto));
    }


    @Test
    public void testInsertedData() {
        final List<DiscoveryHistory> discoveryHistoryList = discoveryRepository.findAll();
        Assertions.assertEquals(4, discoveryHistoryList.size());
    }

    @Test
    public void testInsertedData2() {
        final SearchRequestDto searchRequestDto = new SearchRequestDto();
        final DiscoveryResponseDto responseDto = discoveryService.listDiscoveries(SecurityFilter.create(), searchRequestDto);
        Assertions.assertEquals(4, responseDto.getDiscoveries().size());
    }

    @Test
    public void testInsertedAttributes() {
        var customAttrs = attributeEngine.getObjectCustomAttributesContent(Resource.DISCOVERY, discoveryHistory.getUuid());
        var metaAttrs = attributeEngine.getMetadataAttributesDefinitionContent(new ObjectAttributeContentInfo(Resource.DISCOVERY, discoveryHistory.getUuid()));
        Assertions.assertEquals(1, customAttrs.size());
        Assertions.assertEquals(1, metaAttrs.size());
    }

    @Test
    public void testFilterDataByNameContains() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_NAME.name(), FilterConditionOperator.CONTAINS, "test_discovery"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(4, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByNameEquals() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_NAME.name(), FilterConditionOperator.EQUALS, "test_discovery2"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByStartTime() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_START_TIME.name(), FilterConditionOperator.GREATER, "2020-05-06T10:10:10.000Z"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(2, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByEndTime() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_END_TIME.name(), FilterConditionOperator.LESSER, "2020-02-02T10:10:10.000Z"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByStatus() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_STATUS.name(), FilterConditionOperator.NOT_EQUALS, DiscoveryStatus.COMPLETED.getCode()));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(2, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByTotalCertificateDiscovered() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_TOTAL_CERT_DISCOVERED.name(), FilterConditionOperator.GREATER, 10));
        final SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(filters);
        final DiscoveryResponseDto responseDto = discoveryService.listDiscoveries(SecurityFilter.create(), searchRequestDto);
        Assertions.assertEquals(3, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByKind() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_KIND.name(), FilterConditionOperator.NOT_CONTAINS, "TEST3"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(2, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByConnectorName() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_CONNECTOR_NAME.name(), FilterConditionOperator.EQUALS, "connector1"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(3, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByConnectorNameAndStatus() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_CONNECTOR_NAME.name(), FilterConditionOperator.EQUALS, "connector1"));
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_STATUS.name(), FilterConditionOperator.EQUALS, DiscoveryStatus.COMPLETED.getCode()));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByConnectorNameAndKind() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_CONNECTOR_NAME.name(), FilterConditionOperator.EQUALS, "connector1"));
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_KIND.name(), FilterConditionOperator.STARTS_WITH, "kindTEST"));
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.PROPERTY, SearchableFields.DISCOVERY_START_TIME.name(), FilterConditionOperator.LESSER, "2020-02-01T10:10:10.000Z"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByMetadata() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.META, "attributeMeta1|TEXT", FilterConditionOperator.CONTAINS, "-meta-"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByCustomAttr() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(FilterFieldSource.CUSTOM, "attributeCustom1|TEXT", FilterConditionOperator.CONTAINS, "-custom-"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    private DiscoveryResponseDto retrieveTheDiscoveriesBySearch(final List<SearchFilterRequestDto> filters) {
        final SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(filters);
        return discoveryService.listDiscoveries(SecurityFilter.create(), searchRequestDto);
    }

}

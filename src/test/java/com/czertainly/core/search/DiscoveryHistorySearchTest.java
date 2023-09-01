package com.czertainly.core.search;

import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchGroup;
import com.czertainly.api.model.core.search.SearchableFields;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private AttributeDefinitionRepository attributeDefinitionRepository;
    @Autowired
    private AttributeContentRepository attributeContentRepository;
    @Autowired
    private AttributeContent2ObjectRepository attributeContent2ObjectRepository;
    @Autowired
    private DiscoveryService discoveryService;

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
            discovery1.setKind("kindTEST1");
            discovery1.setStartTime(DATE_FORMAT.parse("2020-01-01T10:10:10"));
            discovery1.setEndTime(DATE_FORMAT.parse("2020-02-01T10:10:10"));
            discovery1.setTotalCertificatesDiscovered(15);
            discoveryHistory = discoveryRepository.save(discovery1);

            final DiscoveryHistory discovery2 = new DiscoveryHistory();
            discovery2.setName("test_discovery2");
            discovery2.setConnectorUuid(connector.getUuid());
            discovery2.setConnectorName("connector1");
            discovery2.setStatus(DiscoveryStatus.COMPLETED);
            discovery2.setKind("kindTEST3");
            discovery2.setStartTime(DATE_FORMAT.parse("2020-05-05T10:10:10"));
            discovery2.setEndTime(DATE_FORMAT.parse("2021-02-01T10:10:10"));
            discovery2.setTotalCertificatesDiscovered(11);
            discoveryRepository.save(discovery2);

            final DiscoveryHistory discovery3 = new DiscoveryHistory();
            discovery3.setName("test_discovery3");
            discovery3.setConnectorUuid(connector.getUuid());
            discovery3.setConnectorName("connector5");
            discovery3.setStatus(DiscoveryStatus.COMPLETED);
            discovery3.setKind("kindTEST3");
            discovery3.setStartTime(DATE_FORMAT.parse("2022-10-01T10:10:10"));
            discovery3.setEndTime(DATE_FORMAT.parse("2023-02-01T10:10:10"));
            discovery3.setTotalCertificatesDiscovered(20);
            discoveryRepository.save(discovery3);

            final DiscoveryHistory discovery4 = new DiscoveryHistory();
            discovery4.setName("test_discovery4");
            discovery4.setConnectorUuid(connector.getUuid());
            discovery4.setConnectorName("connector1");
            discovery4.setStatus(DiscoveryStatus.IN_PROGRESS);
            discovery4.setKind("kindTEST4");
            discovery4.setStartTime(DATE_FORMAT.parse("2020-06-01T10:10:10"));
            discovery4.setEndTime(DATE_FORMAT.parse("2020-10-01T10:10:10"));
            discovery4.setTotalCertificatesDiscovered(5);
            discoveryRepository.save(discovery4);

            loadMetaData();
            loadCustomAttributesData();
            isLoadedData = true;
        } catch (ParseException e) {
            isLoadedData = false;
        }
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
        ac2o.setObjectUuid(discoveryHistory.getUuid());
        ac2o.setObjectType(Resource.DISCOVERY);
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
        ac2o.setObjectUuid(discoveryHistory.getUuid());
        ac2o.setObjectType(Resource.DISCOVERY);
        attributeContent2ObjectRepository.save(ac2o);

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
        final List<AttributeContent2Object> attributeContent2ObjectList = attributeContent2ObjectRepository.findByObjectUuidAndObjectType(discoveryHistory.getUuid(), Resource.DISCOVERY);
        Assertions.assertEquals(2, attributeContent2ObjectList.size());
    }

    @Test
    public void testFilterDataByNameContains() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.NAME.name(), SearchCondition.CONTAINS, "test_discovery"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(4, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByNameEquals() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.NAME.name(), SearchCondition.EQUALS, "test_discovery2"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByStartTime() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.START_TIME.name(), SearchCondition.GREATER, "2020-05-06T10:10:10.000Z"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(2, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByEndTime() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.END_TIME.name(), SearchCondition.LESSER, "2020-02-02T10:10:10.000Z"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByStatus() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.DISCOVERY_STATUS.name(), SearchCondition.NOT_EQUALS, DiscoveryStatus.COMPLETED.getCode()));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(2, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByTotalCertificateDiscovered() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.TOTAL_CERT_DISCOVERED.name(), SearchCondition.GREATER, 10));
        final SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(filters);
        final DiscoveryResponseDto responseDto = discoveryService.listDiscoveries(SecurityFilter.create(), searchRequestDto);
        Assertions.assertEquals(3, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByKind() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.KIND.name(), SearchCondition.NOT_CONTAINS, "TEST3"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(2, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByConnectorName() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.CONNECTOR_NAME.name(), SearchCondition.EQUALS, "connector1"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(3, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByConnectorNameAndStatus() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.CONNECTOR_NAME.name(), SearchCondition.EQUALS, "connector1"));
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.DISCOVERY_STATUS.name(), SearchCondition.EQUALS, DiscoveryStatus.COMPLETED.getCode()));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByConnectorNameAndKind() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.CONNECTOR_NAME.name(), SearchCondition.EQUALS, "connector1"));
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.KIND.name(), SearchCondition.STARTS_WITH, "kindTEST"));
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.PROPERTY, SearchableFields.START_TIME.name(), SearchCondition.LESSER, "2020-02-01T10:10:10.000Z"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByMetadata() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.META, "attributeMeta1|TEXT", SearchCondition.CONTAINS, "-meta-"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    @Test
    public void testFilterDataByCustomAttr() {
        final List<SearchFilterRequestDto> filters = new ArrayList<>();
        filters.add(new SearchFilterRequestDtoDummy(SearchGroup.CUSTOM, "attributeCustom1|TEXT", SearchCondition.CONTAINS, "-custom-"));
        final DiscoveryResponseDto responseDto = retrieveTheDiscoveriesBySearch(filters);
        Assertions.assertEquals(1, responseDto.getDiscoveries().size());
    }

    private DiscoveryResponseDto retrieveTheDiscoveriesBySearch(final List<SearchFilterRequestDto> filters) {
        final SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(filters);
        return discoveryService.listDiscoveries(SecurityFilter.create(), searchRequestDto);
    }

}

package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.vault.VaultInstanceDetailDto;
import com.czertainly.api.model.core.vault.VaultInstanceListResponseDto;
import com.czertainly.api.model.core.vault.VaultInstanceRequestDto;
import com.czertainly.api.model.core.vault.VaultInstanceUpdateRequestDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.VaultInstance;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.VaultInstanceRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class VaultInstanceServiceTest extends BaseSpringBootTest {

    @Autowired
    private VaultInstanceService vaultInstanceService;
    @Autowired
    private VaultInstanceRepository vaultInstanceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private AttributeService attributeService;

    private Connector connector;
    private VaultInstance vaultInstance;

    @BeforeEach
    void setUp() throws AlreadyExistException, AttributeException {
        connector = new Connector();
        connector.setName("test");
        connectorRepository.save(connector);

        vaultInstance = new VaultInstance();
        vaultInstance.setConnector(connector);
        vaultInstance.setConnectorUuid(connector.getUuid());
        vaultInstance.setName("test");
        vaultInstanceRepository.save(vaultInstance);

        CustomAttributeCreateRequestDto dto = new CustomAttributeCreateRequestDto();
        dto.setName("test");
        dto.setLabel("Test Attribute");
        dto.setContentType(AttributeContentType.STRING);
        dto.setResources(List.of(Resource.VAULT));
        attributeService.createCustomAttribute(dto);
    }

    @Test
    void testCreateVaultInstance() throws ConnectorException, NotFoundException, AttributeException, AlreadyExistException {
        VaultInstanceRequestDto requestDto = new VaultInstanceRequestDto();
        requestDto.setName("test");
        requestDto.setConnectorUuid(connector.getUuid());
        RequestAttributeV3 attribute = new RequestAttributeV3();
        attribute.setName("test");
        attribute.setContent(List.of(new StringAttributeContentV3("ref", "data")));
        requestDto.setCustomAttributes(List.of(attribute));
        requestDto.setDescription("description");
        Assertions.assertThrows(AlreadyExistException.class, () -> vaultInstanceService.createVaultInstance(requestDto));
        requestDto.setName("test2");
        VaultInstanceDetailDto vaultInstanceDetailDto = vaultInstanceService.createVaultInstance(requestDto);
        Assertions.assertNotNull(vaultInstanceDetailDto);
        Assertions.assertEquals(requestDto.getName(), vaultInstanceDetailDto.getName());
        Assertions.assertNotNull(vaultInstanceDetailDto.getUuid());
        Assertions.assertEquals(requestDto.getDescription(), vaultInstanceDetailDto.getDescription());
        Assertions.assertNotNull(vaultInstanceDetailDto.getConnectorUuid());
        Assertions.assertEquals(requestDto.getConnectorUuid(), vaultInstanceDetailDto.getConnectorUuid());
        Assertions.assertNotNull(vaultInstanceDetailDto.getCustomAttributes());
        Assertions.assertEquals(1, vaultInstanceDetailDto.getCustomAttributes().size());
        Assertions.assertEquals(attribute.getName(), vaultInstanceDetailDto.getCustomAttributes().getFirst().getName());
        Assertions.assertEquals("data", ((List<AttributeContent>) vaultInstanceDetailDto.getCustomAttributes().getFirst().getContent()).getFirst().getData());
    }

    @Test
    void testGetVaultInstance() throws ConnectorException, NotFoundException, AttributeException {
        Assertions.assertThrows(NotFoundException.class, () -> vaultInstanceService.getVaultInstance(UUID.randomUUID()));
        VaultInstanceDetailDto vaultInstanceDetailDto = vaultInstanceService.getVaultInstance(vaultInstance.getUuid());
        Assertions.assertNotNull(vaultInstanceDetailDto);
        Assertions.assertEquals(vaultInstance.getName(), vaultInstanceDetailDto.getName());
        Assertions.assertEquals(vaultInstance.getDescription(), vaultInstanceDetailDto.getDescription());
        Assertions.assertEquals(vaultInstance.getConnectorUuid(), vaultInstanceDetailDto.getConnectorUuid());
    }

    @Test
    void testListVaultInstances() {
        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.VAULT_INSTANCE_CONNECTOR_NAME.name(), FilterConditionOperator.EQUALS, (Serializable) List.of(connector.getName())),
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.VAULT_INSTANCE_NAME.name(), FilterConditionOperator.EQUALS, vaultInstance.getName())
        ));
        VaultInstanceListResponseDto vaultInstanceListResponseDto = vaultInstanceService.listVaultInstances(searchRequestDto, SecurityFilter.create());
        Assertions.assertNotNull(vaultInstanceListResponseDto);
        Assertions.assertEquals(1, vaultInstanceListResponseDto.getTotalItems());
        Assertions.assertEquals(1, vaultInstanceListResponseDto.getVaultInstances().size());
        Assertions.assertEquals(vaultInstance.getName(), vaultInstanceListResponseDto.getVaultInstances().getFirst().getName());
    }

    @Test
    void testDeleteVaultInstance() throws NotFoundException {
        vaultInstanceService.deleteVaultInstance(vaultInstance.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> vaultInstanceService.getVaultInstance(vaultInstance.getUuid()));
    }

    @Test
    void testUpdateVaultInstance() throws NotFoundException, AttributeException {
        VaultInstanceUpdateRequestDto requestDto = new VaultInstanceUpdateRequestDto();
        Assertions.assertThrows(NotFoundException.class, () -> vaultInstanceService.updateVaultInstance(UUID.randomUUID(), requestDto));
        requestDto.setDescription("new description");
        RequestAttributeV3 attribute = new RequestAttributeV3();
        attribute.setName("test");
        attribute.setContent(List.of(new StringAttributeContentV3("ref", "data")));
        requestDto.setCustomAttributes(List.of(attribute));
        VaultInstanceDetailDto vaultInstanceDetailDto = vaultInstanceService.updateVaultInstance(vaultInstance.getUuid(), requestDto);
        Assertions.assertNotNull(vaultInstanceDetailDto);
        Assertions.assertEquals(requestDto.getDescription(), vaultInstanceDetailDto.getDescription());
        Assertions.assertEquals(1, vaultInstanceDetailDto.getCustomAttributes().size());
        Assertions.assertEquals(attribute.getName(), vaultInstanceDetailDto.getCustomAttributes().getFirst().getName());
        Assertions.assertEquals("data", ((List<AttributeContent>) vaultInstanceDetailDto.getCustomAttributes().getFirst().getContent()).getFirst().getData());
    }

    @Test
    void testGetSearchableFieldInformation() {
        List<SearchFieldDataByGroupDto> searchableFieldInformation = vaultInstanceService.getSearchableFieldInformation();
        Assertions.assertNotNull(searchableFieldInformation);
        Assertions.assertEquals(1, searchableFieldInformation.stream().filter(s -> s.getFilterFieldSource() == FilterFieldSource.CUSTOM).count());
        SearchFieldDataDto connectorNameSearchData = searchableFieldInformation.stream().filter(s -> s.getFilterFieldSource() == FilterFieldSource.PROPERTY)
                .map(SearchFieldDataByGroupDto::getSearchFieldData).flatMap(List::stream)
                .filter(s -> s.getFieldIdentifier().equals(FilterField.VAULT_INSTANCE_CONNECTOR_NAME.name())).findFirst().get();
        Assertions.assertEquals(connector.getName(), ((List<String>) connectorNameSearchData.getValue()).getFirst());
        SearchFieldDataDto nameSearchData = searchableFieldInformation.stream().filter(s -> s.getFilterFieldSource() == FilterFieldSource.PROPERTY)
                .map(SearchFieldDataByGroupDto::getSearchFieldData).flatMap(List::stream)
                .filter(s -> s.getFieldIdentifier().equals(FilterField.VAULT_INSTANCE_NAME.name())).findFirst().get();
        Assertions.assertEquals(vaultInstance.getName(), ((List<String>) nameSearchData.getValue()).getFirst());
        Assertions.assertEquals(1, searchableFieldInformation.stream().filter(s -> s.getFilterFieldSource() == FilterFieldSource.PROPERTY).count());
    }


}

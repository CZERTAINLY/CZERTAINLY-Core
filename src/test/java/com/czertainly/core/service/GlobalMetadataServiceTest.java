package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.metadata.ConnectorMetadataResponseDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataCreateRequestDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.metadata.GlobalMetadataUpdateRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentRepository;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GlobalMetadataServiceTest extends BaseSpringBootTest {

    @Autowired
    private AttributeService attributeService;

    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;

    @Autowired
    private AttributeContentRepository attributeContentRepository;

    @Autowired
    private AttributeContent2ObjectRepository attributeContent2ObjectRepository;

    @Autowired
    private AttributeRelationRepository attributeRelationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    private Connector connector;
    private AttributeDefinition definition;
    private DataAttribute attribute;
    private MetadataAttribute metaAttribute;
    private MetadataAttribute connectorMetaAttribute;
    private AttributeDefinition metaDefinition;
    private AttributeDefinition connectorMetaDefinition;

    @BeforeEach
    public void setUp() {
        connector = new Connector();
        connector.setName("test");
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        attribute = new DataAttribute();
        attribute.setUuid("87e968ca-9404-4128-8b58-3ab5db2ba06e");
        attribute.setName("attribute");
        attribute.setDescription("Attribute");
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(AttributeContentType.STRING);
        DataAttributeProperties urlProperties = new DataAttributeProperties();
        urlProperties.setLabel("Attribute");
        urlProperties.setRequired(true);
        urlProperties.setReadOnly(false);
        urlProperties.setVisible(true);
        urlProperties.setList(false);
        urlProperties.setMultiSelect(false);
        attribute.setProperties(urlProperties);

        metaAttribute = new MetadataAttribute();
        metaAttribute.setUuid("87e968ca-9404-4128-8b58-3ab5db2ba07e");
        metaAttribute.setName("attribute1");
        metaAttribute.setDescription("Attribute1");
        metaAttribute.setType(AttributeType.META);
        metaAttribute.setContentType(AttributeContentType.STRING);
        MetadataAttributeProperties properties = new MetadataAttributeProperties();
        properties.setLabel("Attribute1");
        properties.setVisible(true);
        metaAttribute.setProperties(properties);

        connectorMetaAttribute = new MetadataAttribute();
        connectorMetaAttribute.setUuid("87e968ca-9404-4128-8b58-3ab5db2ba08e");
        connectorMetaAttribute.setName("connectorAttribute");
        connectorMetaAttribute.setDescription("Connector Attribute");
        connectorMetaAttribute.setType(AttributeType.META);
        connectorMetaAttribute.setContentType(AttributeContentType.STRING);
        MetadataAttributeProperties connectorProperties = new MetadataAttributeProperties();
        connectorProperties.setLabel("Connector Attribute");
        connectorProperties.setVisible(true);
        connectorMetaAttribute.setProperties(connectorProperties);

        definition = new AttributeDefinition();
        definition.setAttributeDefinition(attribute);
        definition.setAttributeName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setContentType(attribute.getContentType());
        definition.setType(AttributeType.CUSTOM);
        definition.setEnabled(true);
        attributeDefinitionRepository.save(definition);

        metaDefinition = new AttributeDefinition();
        metaDefinition.setAttributeDefinition(metaAttribute);
        metaDefinition.setAttributeName(metaAttribute.getName());
        metaDefinition.setAttributeUuid(UUID.fromString(metaAttribute.getUuid()));
        metaDefinition.setContentType(metaAttribute.getContentType());
        metaDefinition.setType(AttributeType.META);
        metaDefinition.setGlobal(true);
        attributeDefinitionRepository.save(metaDefinition);

        connectorMetaDefinition = new AttributeDefinition();
        connectorMetaDefinition.setAttributeDefinition(connectorMetaAttribute);
        connectorMetaDefinition.setAttributeName(connectorMetaAttribute.getName());
        connectorMetaDefinition.setAttributeUuid(UUID.fromString(connectorMetaAttribute.getUuid()));
        connectorMetaDefinition.setContentType(connectorMetaAttribute.getContentType());
        connectorMetaDefinition.setType(AttributeType.META);
        connectorMetaDefinition.setConnectorUuid(connector.getUuid());
        connectorMetaDefinition.setGlobal(false);
        attributeDefinitionRepository.save(connectorMetaDefinition);
    }

    @Test
    public void testListGlobalMetadata() {
        List<AttributeDefinitionDto> metadata = attributeService.listAttributes(SecurityFilter.create(), AttributeType.META);
        Assertions.assertNotNull(metadata);
        Assertions.assertFalse(metadata.isEmpty());
        Assertions.assertEquals(1, metadata.size());
        Assertions.assertEquals(metaDefinition.getUuid().toString(), metadata.get(0).getUuid());
    }

    @Test
    public void testGetGlobalMetadata() throws NotFoundException {
        GlobalMetadataDefinitionDetailDto dto = attributeService.getGlobalMetadata(SecuredUUID.fromUUID(metaDefinition.getUuid()));
        Assertions.assertNotNull(dto);
        Assertions.assertFalse(dto.getUuid().isEmpty());
        Assertions.assertEquals(metaDefinition.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(metaAttribute.getName(), dto.getName());
        Assertions.assertEquals(metaAttribute.getType(), AttributeType.META);
        Assertions.assertEquals(metaAttribute.getContentType(), AttributeContentType.STRING);
    }

    @Test
    public void testGlobalAttributeNotFound() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getGlobalMetadata(SecuredUUID.fromString(attribute.getUuid())));
    }

    @Test
    public void testCreateGlobalMetadata() throws ValidationException, AlreadyExistException {
        GlobalMetadataCreateRequestDto request = new GlobalMetadataCreateRequestDto();
        request.setName("testAttribute");
        request.setDescription("Sample description");
        request.setLabel("TestAttribute");
        request.setContentType(AttributeContentType.STRING);
        request.setVisible(true);

        GlobalMetadataDefinitionDetailDto response = attributeService.createGlobalMetadata(request);
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.getUuid().isEmpty());
        Assertions.assertEquals(request.getName(), response.getName());
        Assertions.assertEquals(response.getType(), AttributeType.META);
        Assertions.assertEquals(request.getContentType(), AttributeContentType.STRING);
    }

    @Test
    public void testCreateGlobalMetadataAlreadyExistsException() throws ValidationException, AlreadyExistException {
        GlobalMetadataCreateRequestDto request = new GlobalMetadataCreateRequestDto();
        request.setName("attribute1");
        request.setDescription("Attribute");
        request.setLabel("Attribute");
        request.setContentType(AttributeContentType.STRING);
        request.setVisible(true);

        Assertions.assertThrows(AlreadyExistException.class, () -> attributeService.createGlobalMetadata(request));
    }

    @Test
    public void testCreateGlobalMetadataValidationException() throws ValidationException, AlreadyExistException {
        GlobalMetadataCreateRequestDto request = new GlobalMetadataCreateRequestDto();

        Assertions.assertThrows(ValidationException.class, () -> attributeService.createGlobalMetadata(request));
    }

    @Test
    public void testEditGlobalMetadata() throws NotFoundException {
        GlobalMetadataUpdateRequestDto request = new GlobalMetadataUpdateRequestDto();
        request.setLabel("Updated Attribute");
        request.setDescription("Desc");

        GlobalMetadataDefinitionDetailDto response = attributeService.editGlobalMetadata(SecuredUUID.fromUUID(metaDefinition.getUuid()), request);
        Assertions.assertEquals(request.getDescription(), response.getDescription());
        Assertions.assertEquals(request.getLabel(), response.getLabel());
    }

    @Test
    public void testEditGlobalMetadataNotFoundException() throws NotFoundException {
        GlobalMetadataUpdateRequestDto request = new GlobalMetadataUpdateRequestDto();
        request.setLabel("Updated Attribute");
        request.setDescription("Desc");

        Assertions.assertThrows(NotFoundException.class, () -> attributeService.editGlobalMetadata(SecuredUUID.fromString(attribute.getUuid()), request));
    }

    @Test
    public void testGlobalMetadataAttribute() throws NotFoundException {
        attributeService.deleteAttribute(SecuredUUID.fromUUID(metaDefinition.getUuid()), AttributeType.META);
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getGlobalMetadata(SecuredUUID.fromUUID(metaDefinition.getUuid())));
    }

    @Test
    public void testDeleteGlobalMetadataNotFoundException() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.deleteAttribute(SecuredUUID.fromString(attribute.getUuid()), AttributeType.META));
    }

    @Test
    public void testBulkDeleteGlobalMetadata() throws NotFoundException {
        attributeService.bulkDeleteAttributes(List.of(SecuredUUID.fromUUID(metaDefinition.getUuid())), AttributeType.META);
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getAttribute(SecuredUUID.fromUUID(metaDefinition.getUuid())));
    }

    @Test
    public void getConnectorMetadata() {
        List<ConnectorMetadataResponseDto> metadata = attributeService.getConnectorMetadata(Optional.empty());
        Assertions.assertEquals(1, metadata.size());
        Assertions.assertEquals(metadata.get(0).getUuid(), connectorMetaAttribute.getUuid());
        Assertions.assertEquals(metadata.get(0).getName(), connectorMetaAttribute.getName());
    }

    @Test
    public void getConnectorMetadata_ItemNotFound() {
        connectorMetaDefinition.setConnectorUuid(null);
        attributeDefinitionRepository.save(connectorMetaDefinition);
        List<ConnectorMetadataResponseDto> metadata = attributeService.getConnectorMetadata(Optional.empty());
        Assertions.assertTrue(metadata.isEmpty());
    }

    @Test
    public void promoteConnectorMetadata() throws NotFoundException {
        attributeService.promoteConnectorMetadata(UUID.fromString(connectorMetaAttribute.getUuid()), connector.getUuid());
        List<AttributeDefinitionDto> dto = attributeService.listAttributes(SecurityFilter.create(), AttributeType.META);
        Assertions.assertEquals(2, dto.size());
    }
}

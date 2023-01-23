package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.AttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentRepository;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

public class CustomAttributeServiceTest extends BaseSpringBootTest {

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

    private AttributeDefinition definition;
    private DataAttribute attribute;
    private MetadataAttribute metaAttribute;
    private AttributeDefinition metaDefinition;

    @BeforeEach
    public void setUp() {
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
        urlProperties.setLabel("Attribute1");
        urlProperties.setVisible(true);
        metaAttribute.setProperties(properties);

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
        metaDefinition.setEnabled(true);
        attributeDefinitionRepository.save(metaDefinition);
    }

    @Test
    public void testListAttributes() {
        List<AttributeDefinitionDto> attributes = attributeService.listAttributes(SecurityFilter.create(), AttributeType.CUSTOM);
        Assertions.assertNotNull(attributes);
        Assertions.assertFalse(attributes.isEmpty());
        Assertions.assertEquals(1, attributes.size());
        Assertions.assertEquals(attribute.getUuid(), attributes.get(0).getUuid());
    }

    @Test
    public void testGetAttribute() throws NotFoundException {
        CustomAttributeDefinitionDetailDto dto = attributeService.getAttribute(SecuredUUID.fromUUID(definition.getUuid()));
        Assertions.assertNotNull(dto);
        Assertions.assertFalse(dto.getUuid().isEmpty());
        Assertions.assertEquals(attribute.getUuid(), dto.getUuid());
        Assertions.assertEquals(attribute.getName(), dto.getName());
        Assertions.assertEquals(attribute.getType(), AttributeType.CUSTOM);
        Assertions.assertEquals(attribute.getContentType(), AttributeContentType.STRING);
    }

    @Test
    public void testGetAttributeNotFound() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getAttribute(SecuredUUID.fromUUID(metaDefinition.getUuid())));
    }

    @Test
    public void testCreateAttribute() throws ValidationException, AlreadyExistException {
        CustomAttributeCreateRequestDto request = new CustomAttributeCreateRequestDto();
        request.setName("testAttribute");
        request.setDescription("Sample description");
        request.setLabel("TestAttribute");
        request.setContentType(AttributeContentType.STRING);
        request.setRequired(true);
        request.setVisible(true);
        request.setResources(List.of(Resource.USER, Resource.ROLE));

        CustomAttributeDefinitionDetailDto response = attributeService.createAttribute(request);
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.getUuid().isEmpty());
        Assertions.assertEquals(request.getName(), response.getName());
        Assertions.assertEquals(response.getType(), AttributeType.CUSTOM);
        Assertions.assertEquals(request.getContentType(), AttributeContentType.STRING);
        Assertions.assertEquals(2, request.getResources().size());
    }

    @Test
    public void testCreateAttributeAlreadyExistsException() throws ValidationException, AlreadyExistException {
        CustomAttributeCreateRequestDto request = new CustomAttributeCreateRequestDto();
        request.setName("attribute");
        request.setDescription("Attribute");
        request.setLabel("Attribute");
        request.setContentType(AttributeContentType.STRING);
        request.setRequired(true);
        request.setVisible(true);

        Assertions.assertThrows(AlreadyExistException.class, () -> attributeService.createAttribute(request));
    }

    @Test
    public void testCreateAttributeValidationException() throws ValidationException, AlreadyExistException {
        CustomAttributeCreateRequestDto request = new CustomAttributeCreateRequestDto();

        Assertions.assertThrows(ValidationException.class, () -> attributeService.createAttribute(request));
    }

    @Test
    public void testEditAttribute() throws NotFoundException {
        CustomAttributeUpdateRequestDto request = new CustomAttributeUpdateRequestDto();
        request.setLabel("Updated Attribute");
        request.setDescription("Desc");
        request.setResources(List.of(Resource.RA_PROFILE));

        CustomAttributeDefinitionDetailDto response = attributeService.editAttribute(SecuredUUID.fromUUID(definition.getUuid()), request);
        Assertions.assertEquals(request.getDescription(), response.getDescription());
        Assertions.assertEquals(request.getLabel(), response.getLabel());
        Assertions.assertEquals(1, response.getResources().size());
    }

    @Test
    public void testEditAttributeNotFoundException() throws NotFoundException {
        CustomAttributeUpdateRequestDto request = new CustomAttributeUpdateRequestDto();
        request.setLabel("Updated Attribute");
        request.setDescription("Desc");

        Assertions.assertThrows(NotFoundException.class, () -> attributeService.editAttribute(SecuredUUID.fromUUID(metaDefinition.getUuid()), request));
    }

    @Test
    public void testEnableAttribute() throws NotFoundException {
        attributeService.enableAttribute(SecuredUUID.fromUUID(definition.getUuid()), AttributeType.CUSTOM);
        CustomAttributeDefinitionDetailDto dto = attributeService.getAttribute(SecuredUUID.fromUUID(definition.getUuid()));
        Assertions.assertEquals(true, dto.isEnabled());
    }

    @Test
    public void testEnableAttributeNotFoundException() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.enableAttribute(SecuredUUID.fromUUID(metaDefinition.getUuid()), AttributeType.CUSTOM));
    }

    @Test
    public void testDisableAttribute() throws NotFoundException {
        attributeService.disableAttribute(SecuredUUID.fromUUID(definition.getUuid()), AttributeType.CUSTOM);
        CustomAttributeDefinitionDetailDto dto = attributeService.getAttribute(SecuredUUID.fromUUID(definition.getUuid()));
        Assertions.assertEquals(false, dto.isEnabled());
    }

    @Test
    public void testDisableAttributeNotFoundException() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.disableAttribute(SecuredUUID.fromUUID(metaDefinition.getUuid()), AttributeType.CUSTOM));
    }

    @Test
    public void testDeleteAttribute() throws NotFoundException {
        attributeService.deleteAttribute(SecuredUUID.fromUUID(definition.getUuid()), AttributeType.CUSTOM);
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getAttribute(SecuredUUID.fromUUID(definition.getUuid())));
    }

    @Test
    public void testDeleteAttributeNotFoundException() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.disableAttribute(SecuredUUID.fromUUID(metaDefinition.getUuid()), AttributeType.CUSTOM));
    }

    @Test
    public void testBulkEnableAttribute() throws NotFoundException {
        attributeService.bulkEnableAttributes(List.of(SecuredUUID.fromUUID(definition.getUuid())), AttributeType.CUSTOM);
        CustomAttributeDefinitionDetailDto dto = attributeService.getAttribute(SecuredUUID.fromUUID(definition.getUuid()));
        Assertions.assertEquals(true, dto.isEnabled());
    }

    @Test
    public void testBulkDisableAttribute() throws NotFoundException {
        attributeService.bulkDisableAttributes(List.of(SecuredUUID.fromUUID(definition.getUuid())), AttributeType.CUSTOM);
        CustomAttributeDefinitionDetailDto dto = attributeService.getAttribute(SecuredUUID.fromUUID(definition.getUuid()));
        Assertions.assertEquals(false, dto.isEnabled());
    }

    @Test
    public void testBulkDeleteAttribute() throws NotFoundException {
        attributeService.bulkDeleteAttributes(List.of(SecuredUUID.fromUUID(definition.getUuid())), AttributeType.CUSTOM);
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getAttribute(SecuredUUID.fromUUID(definition.getUuid())));
    }

    @Test
    public void testUpdateResource() throws NotFoundException {
        attributeService.updateResources(SecuredUUID.fromUUID(definition.getUuid()), List.of(Resource.ROLE, Resource.CREDENTIAL));
        List<BaseAttribute> attributes = attributeService.getResourceAttributes(Resource.ROLE);
        Assertions.assertEquals(1, attributes.size());
        Assertions.assertEquals(attribute.getUuid(), attributes.get(0).getUuid());
    }

    @Test
    public void testUpdateResourceFailure() throws ValidationException, NotFoundException {
        Assertions.assertThrows(ValidationException.class, () -> attributeService.updateResources(SecuredUUID.fromUUID(definition.getUuid()), List.of(Resource.DASHBOARD)));
    }

    @Test
    public void testGetResources() {
        List<Resource> resources = attributeService.getResources();
        Assertions.assertEquals(16, resources.size());
    }

    @Test
    public void testGetResourceAttributesWithValue() throws NotFoundException {
        attributeService.updateResources(SecuredUUID.fromUUID(definition.getUuid()), List.of(Resource.ROLE, Resource.CREDENTIAL));
        List<BaseAttribute> attributes = attributeService.getResourceAttributes(Resource.ROLE);
        Assertions.assertEquals(1, attributes.size());
    }
}

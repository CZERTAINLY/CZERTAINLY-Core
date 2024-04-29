package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeUpdateRequestDto;
import com.czertainly.api.model.common.attribute.v2.*;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
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

    private AttributeDefinition definition;
    private CustomAttribute attribute;
    private MetadataAttribute metaAttribute;
    private AttributeDefinition metaDefinition;

    @BeforeEach
    public void setUp() {
        attribute = new CustomAttribute();
        attribute.setUuid("87e968ca-9404-4128-8b58-3ab5db2ba06e");
        attribute.setName("attribute");
        attribute.setDescription("Attribute");
        attribute.setType(AttributeType.CUSTOM);
        attribute.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties urlProperties = new CustomAttributeProperties();
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

        definition = new AttributeDefinition();
        definition.setDefinition(attribute);
        definition.setName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setContentType(attribute.getContentType());
        definition.setType(AttributeType.CUSTOM);
        definition.setEnabled(true);
        definition.setLabel(attribute.getProperties().getLabel());
        definition.setRequired(attribute.getProperties().isRequired());
        definition.setReadOnly(attribute.getProperties().isReadOnly());
        attributeDefinitionRepository.save(definition);

        metaDefinition = new AttributeDefinition();
        metaDefinition.setDefinition(metaAttribute);
        metaDefinition.setName(metaAttribute.getName());
        metaDefinition.setAttributeUuid(UUID.fromString(metaAttribute.getUuid()));
        metaDefinition.setContentType(metaAttribute.getContentType());
        metaDefinition.setType(AttributeType.META);
        metaDefinition.setEnabled(true);
        metaDefinition.setLabel(metaAttribute.getProperties().getLabel());
        attributeDefinitionRepository.save(metaDefinition);
    }

    @Test
    public void testListAttributes() {
        List<CustomAttributeDefinitionDto> attributes = attributeService.listCustomAttributes(SecurityFilter.create(), null);
        Assertions.assertNotNull(attributes);
        Assertions.assertFalse(attributes.isEmpty());
        Assertions.assertEquals(1, attributes.size());
        Assertions.assertEquals(attribute.getUuid(), attributes.get(0).getUuid());
    }

    @Test
    public void testGetAttribute() throws NotFoundException {
        CustomAttributeDefinitionDetailDto dto = attributeService.getCustomAttribute(definition.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertFalse(dto.getUuid().isEmpty());
        Assertions.assertEquals(attribute.getUuid(), dto.getUuid());
        Assertions.assertEquals(attribute.getName(), dto.getName());
        Assertions.assertEquals(attribute.getType(), AttributeType.CUSTOM);
        Assertions.assertEquals(attribute.getContentType(), AttributeContentType.STRING);
    }

    @Test
    public void testGetAttributeNotFound() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getCustomAttribute(metaDefinition.getUuid()));
    }

    @Test
    public void testCreateAttribute() throws ValidationException, AlreadyExistException, AttributeException {
        CustomAttributeCreateRequestDto request = new CustomAttributeCreateRequestDto();
        request.setName("testAttribute");
        request.setDescription("Sample description");
        request.setLabel("TestAttribute");
        request.setContentType(AttributeContentType.STRING);
        request.setRequired(true);
        request.setVisible(true);
        request.setResources(List.of(Resource.USER, Resource.ROLE));

        CustomAttributeDefinitionDetailDto response = attributeService.createCustomAttribute(request);
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

        Assertions.assertThrows(AlreadyExistException.class, () -> attributeService.createCustomAttribute(request));
    }

    @Test
    public void testCreateAttributeValidationException() throws ValidationException, AlreadyExistException {
        CustomAttributeCreateRequestDto request = new CustomAttributeCreateRequestDto();

        Assertions.assertThrows(AttributeException.class, () -> attributeService.createCustomAttribute(request));
    }

    @Test
    public void testEditAttribute() throws NotFoundException, AttributeException {
        CustomAttributeUpdateRequestDto request = new CustomAttributeUpdateRequestDto();
        request.setLabel("Updated Attribute");
        request.setDescription("Desc");
        request.setResources(List.of(Resource.RA_PROFILE));

        CustomAttributeDefinitionDetailDto response = attributeService.editCustomAttribute(definition.getUuid(), request);
        Assertions.assertEquals(request.getDescription(), response.getDescription());
        Assertions.assertEquals(request.getLabel(), response.getLabel());
        Assertions.assertEquals(1, response.getResources().size());
    }

    @Test
    public void testEditAttributeNotFoundException() throws NotFoundException {
        CustomAttributeUpdateRequestDto request = new CustomAttributeUpdateRequestDto();
        request.setLabel("Updated Attribute");
        request.setDescription("Desc");

        Assertions.assertThrows(NotFoundException.class, () -> attributeService.editCustomAttribute(metaDefinition.getUuid(), request));
    }

    @Test
    public void testEnableAttribute() throws NotFoundException {
        attributeService.enableCustomAttribute(definition.getUuid(), true);
        CustomAttributeDefinitionDetailDto dto = attributeService.getCustomAttribute(definition.getUuid());
        Assertions.assertEquals(true, dto.isEnabled());
    }

    @Test
    public void testEnableAttributeNotFoundException() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.enableCustomAttribute(metaDefinition.getUuid(), true));
    }

    @Test
    public void testDisableAttribute() throws NotFoundException {
        attributeService.enableCustomAttribute(definition.getUuid(), false);
        CustomAttributeDefinitionDetailDto dto = attributeService.getCustomAttribute(definition.getUuid());
        Assertions.assertEquals(false, dto.isEnabled());
    }

    @Test
    public void testDisableAttributeNotFoundException() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.enableCustomAttribute(metaDefinition.getUuid(), false));
    }

    @Test
    public void testDeleteAttribute() throws NotFoundException {
        attributeService.deleteCustomAttribute(definition.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getCustomAttribute(definition.getUuid()));
    }

    @Test
    public void testDeleteAttributeNotFoundException() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.deleteCustomAttribute(metaDefinition.getUuid()));
    }

    @Test
    public void testBulkEnableAttribute() throws NotFoundException {
        attributeService.bulkEnableCustomAttributes(List.of(definition.getUuid().toString()), true);
        CustomAttributeDefinitionDetailDto dto = attributeService.getCustomAttribute(definition.getUuid());
        Assertions.assertEquals(true, dto.isEnabled());
    }

    @Test
    public void testBulkDisableAttribute() throws NotFoundException {
        attributeService.bulkEnableCustomAttributes(List.of(definition.getUuid().toString()), false);
        CustomAttributeDefinitionDetailDto dto = attributeService.getCustomAttribute(definition.getUuid());
        Assertions.assertEquals(false, dto.isEnabled());
    }

    @Test
    public void testBulkDeleteAttribute() throws NotFoundException {
        attributeService.bulkDeleteCustomAttributes(List.of(definition.getUuid().toString()));
        Assertions.assertThrows(NotFoundException.class, () -> attributeService.getCustomAttribute(definition.getUuid()));
    }

    @Test
    public void testUpdateResource() throws NotFoundException {
        attributeService.updateResources(definition.getUuid(), List.of(Resource.ROLE, Resource.CREDENTIAL));
        List<BaseAttribute> attributes = attributeService.getResourceAttributes(SecurityFilter.create(), Resource.ROLE);
        Assertions.assertEquals(1, attributes.size());
        Assertions.assertEquals(attribute.getUuid(), attributes.get(0).getUuid());
    }

    @Test
    public void testUpdateResourceFailure() throws ValidationException, NotFoundException {
        Assertions.assertThrows(ValidationException.class, () -> attributeService.updateResources(definition.getUuid(), List.of(Resource.AUDIT_LOG)));
    }

    @Test
    public void testGetResourceAttributesWithValue() throws NotFoundException {
        attributeService.updateResources(definition.getUuid(), List.of(Resource.ROLE, Resource.CREDENTIAL));
        List<BaseAttribute> attributes = attributeService.getResourceAttributes(SecurityFilter.create(), Resource.ROLE);
        Assertions.assertEquals(1, attributes.size());
    }
}

package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.client.metadata.ResponseMetadataDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.InfoAttribute;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.core.dao.entity.AttributeContent2Object;
import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentRepository;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.MetadataService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class MetadataServiceImpl implements MetadataService {

    private AttributeDefinitionRepository metadataDefinitionRepository;
    private AttributeContentRepository metadataContentRepository;
    private AttributeContent2ObjectRepository metadata2ObjectRepository;

    @Autowired
    public void setMetadataDefinitionRepository(AttributeDefinitionRepository metadataDefinitionRepository) {
        this.metadataDefinitionRepository = metadataDefinitionRepository;
    }

    @Autowired
    public void setMetadataContentRepository(AttributeContentRepository metadataContentRepository) {
        this.metadataContentRepository = metadataContentRepository;
    }

    @Autowired
    public void setMetadata2ObjectRepository(AttributeContent2ObjectRepository metadata2ObjectRepository) {
        this.metadata2ObjectRepository = metadata2ObjectRepository;
    }

    @Override
    public void createMetadataDefinitions(UUID connectorUuid, List<InfoAttribute> metadataDefinitions) {
        if (metadataDefinitions == null) {
            return;
        }
        for (InfoAttribute infoAttribute : metadataDefinitions) {
            if (!metadataDefinitionRepository.existsByConnectorUuidAndAttributeUuidAndTypeAndContentType(connectorUuid, UUID.fromString(infoAttribute.getUuid()), infoAttribute.getType(), infoAttribute.getContentType())) {
                createMetadataDefinition(connectorUuid, infoAttribute);
            } else {
                AttributeDefinition definition = metadataDefinitionRepository.findByConnectorUuidAndAttributeNameAndAttributeUuidAndTypeAndContentType(connectorUuid, infoAttribute.getName(), null, infoAttribute.getType(), infoAttribute.getContentType()).orElse(null);
                if (definition != null) {
                    definition.setAttributeUuid(UUID.fromString(infoAttribute.getUuid()));
                }
            }
        }
    }

    @Override
    public void createMetadata(UUID connectorUuid, UUID objectUuid, UUID sourceObjectUuid, List<InfoAttribute> metadata, Resource resource, Resource sourceObjectResource) {
        if (metadata == null) {
            return;
        }
        for (InfoAttribute infoAttribute : metadata) {
            createMetadataContent(connectorUuid, objectUuid, UUID.fromString(infoAttribute.getUuid()), sourceObjectUuid, infoAttribute.getContent(), resource, sourceObjectResource);
        }
    }

    @Override
    public List<InfoAttribute> getMetadata(UUID connectorUuid, UUID uuid, Resource resource) {
        List<InfoAttribute> metadata = new ArrayList<>();
        for (AttributeContent2Object object : metadata2ObjectRepository.findByObjectUuidAndObjectType(uuid, resource)) {
            if (object.getAttributeContent().getAttributeDefinition().getConnectorUuid().equals(connectorUuid)) {
                InfoAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition();
                attribute.setContent(object.getAttributeContent().getAttributeContent(BaseAttributeContent.class));
                metadata.add(attribute);
            }
        }
        return metadata;
    }

    @Override
    public List<InfoAttribute> getMetadataWithSource(UUID connectorUuid, UUID uuid, Resource resource, UUID sourceObjectUuid, Resource sourceObjectResource) {
        List<InfoAttribute> metadata = new ArrayList<>();
        for (AttributeContent2Object object : metadata2ObjectRepository.findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(uuid, resource, sourceObjectUuid, sourceObjectResource)) {
            if (object.getAttributeContent().getAttributeDefinition().getConnectorUuid().equals(connectorUuid)) {
                InfoAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition();
                attribute.setContent(object.getAttributeContent().getAttributeContent(BaseAttributeContent.class));
                metadata.add(attribute);
            }
        }
        return metadata;
    }

    @Override
    public List<MetadataResponseDto> getFullMetadata(UUID uuid, Resource resource, UUID sourceObjectUuid, Resource sourceObjectResource) {
        List<MetadataResponseDto> metadataResponses = new ArrayList<>();
        Map<String, List<ResponseMetadataDto>> metadata = new HashMap<>();
        for (AttributeContent2Object object : metadata2ObjectRepository.findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(uuid, resource, sourceObjectUuid, sourceObjectResource)) {
            InfoAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition();
            ResponseMetadataDto responseMetadataDto = new ResponseMetadataDto();
            responseMetadataDto.setType(AttributeType.META);
            responseMetadataDto.setContentType(attribute.getContentType());
            if (attribute.getProperties() != null) {
                responseMetadataDto.setLabel(attribute.getProperties().getLabel());
            }
            responseMetadataDto.setContent(object.getAttributeContent().getAttributeContent(BaseAttributeContent.class));
            responseMetadataDto.setName(attribute.getName());
            responseMetadataDto.setUuid(attribute.getUuid());
            responseMetadataDto.setSourceObjectType(object.getSourceObjectType() != null ? object.getSourceObjectType().getCode() : null);
            responseMetadataDto.setSourceObjectUuid(object.getSourceObjectUuid() != null ? object.getSourceObjectUuid().toString() : null);
            Connector connector = object.getAttributeContent().getAttributeDefinition().getConnector();
            String key;
            if (connector == null) {
                key = ":#";
            } else {
                key = connector.getName() + ":#" + connector.getUuid().toString();
            }
            metadata.computeIfAbsent(key, k -> new ArrayList<>()).add(responseMetadataDto);
        }

        for (Map.Entry<String, List<ResponseMetadataDto>> entry : metadata.entrySet()) {
            MetadataResponseDto metadataResponse = new MetadataResponseDto();
            if (entry.getKey().equals(":#")) {
                metadataResponse.setConnectorUuid("Unknown");
                metadataResponse.setConnectorName("Unknown");
            } else {
                String[] split = entry.getKey().split(":#");
                metadataResponse.setConnectorName(split[0]);
                metadataResponse.setConnectorUuid(split[1]);
            }
            metadataResponse.setItems(entry.getValue());
            metadataResponses.add(metadataResponse);
        }
        return metadataResponses;
    }

    private void createMetadataDefinition(UUID connectorUuid, InfoAttribute attribute) {
        AttributeDefinition definition = new AttributeDefinition();
        definition.setAttributeDefinition(attribute);
        definition.setAttributeName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setConnectorUuid(connectorUuid);
        definition.setContentType(attribute.getContentType());
        definition.setType(AttributeType.META);
        metadataDefinitionRepository.save(definition);
    }

    private void createMetadataContent(UUID connectorUuid, UUID objectUuid, UUID attributeUuid, UUID sourceObjectUuid, List<BaseAttributeContent> metadata, Resource resource, Resource sourceObjectResource) {
        String serializedContent = AttributeDefinitionUtils.serializeAttributeContent(metadata);
        AttributeDefinition definition = metadataDefinitionRepository.findByConnectorUuidAndAttributeUuid(connectorUuid, attributeUuid).orElse(null);
        AttributeContent existingContent = metadataContentRepository.findByAttributeContentAndAttributeDefinition(serializedContent, definition).orElse(null);

        AttributeContent2Object metadata2Object = new AttributeContent2Object();
        metadata2Object.setObjectUuid(objectUuid);
        metadata2Object.setObjectType(resource);
        metadata2Object.setSourceObjectUuid(sourceObjectUuid);
        metadata2Object.setSourceObjectType(sourceObjectResource);
        metadata2Object.setConnectorUuid(connectorUuid);

        if (existingContent != null) {
            metadata2Object.setAttributeContent(existingContent);
        } else {
            AttributeContent content = new AttributeContent();
            content.setAttributeContent(metadata);
            content.setAttributeDefinition(definition);
            metadataContentRepository.save(content);
            metadata2Object.setAttributeContent(content);
        }
        metadata2ObjectRepository.save(metadata2Object);
    }
}

package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.client.metadata.ResponseMetadataDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeContent2Object;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.AttributeContent2ObjectRepository;
import com.czertainly.core.dao.repository.AttributeContentRepository;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.service.MetadataService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class MetadataServiceImpl implements MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataServiceImpl.class);

    private AttributeDefinitionRepository metadataDefinitionRepository;
    private AttributeContentRepository metadataContentRepository;
    private AttributeContent2ObjectRepository metadata2ObjectRepository;
    private ConnectorRepository connectorRepository;

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

    @Autowired
    public void setConnectorRepository(ConnectorRepository connectorRepository) {
        this.connectorRepository = connectorRepository;
    }

    @Override
    public void createMetadataDefinitions(UUID connectorUuid, List<MetadataAttribute> metadataDefinitions) {
        if (metadataDefinitions == null) {
            return;
        }
        for (MetadataAttribute metadataAttribute : metadataDefinitions) {
            if (metadataAttribute.getProperties() != null && metadataAttribute.getProperties().isGlobal()) {
                Optional<AttributeDefinition> definition = metadataDefinitionRepository.findByTypeAndAttributeNameAndGlobalAndContentType(metadataAttribute.getType(), metadataAttribute.getName(), true, metadataAttribute.getContentType());
                if (definition.isPresent()) continue;
            }
            if (!metadataDefinitionRepository.existsByConnectorUuidAndAttributeUuidAndTypeAndContentType(connectorUuid, UUID.fromString(metadataAttribute.getUuid()), metadataAttribute.getType(), metadataAttribute.getContentType())) {
                AttributeDefinition definition = metadataDefinitionRepository.findByConnectorUuidAndAttributeNameAndAttributeUuidAndTypeAndContentType(connectorUuid, metadataAttribute.getName(), null, metadataAttribute.getType(), metadataAttribute.getContentType()).orElse(null);
                if (definition != null) {
                    definition.setAttributeUuid(UUID.fromString(metadataAttribute.getUuid()));
                } else {
                    createMetadataDefinition(connectorUuid, metadataAttribute);
                }
            }
        }
    }

    @Override
    public void createMetadata(UUID connectorUuid, UUID objectUuid, UUID sourceObjectUuid, String sourceObjectName, List<MetadataAttribute> metadata, Resource resource, Resource sourceObjectResource) {
        if (metadata == null) {
            return;
        }

        Optional<Connector> connector = connectorRepository.findByUuid(connectorUuid);
        for (MetadataAttribute metadataAttribute : metadata) {
            createMetadataContent(metadataAttribute.getName(), metadataAttribute.getContentType(), connector.isPresent() ? connector.get() : null, objectUuid, UUID.fromString(metadataAttribute.getUuid()), sourceObjectUuid, sourceObjectName, metadataAttribute.getContent(), resource, sourceObjectResource, metadataAttribute.getProperties());
        }
    }

    @Override
    public List<MetadataAttribute> getMetadata(UUID connectorUuid, UUID uuid, Resource resource) {
        List<MetadataAttribute> metadata = new ArrayList<>();
        for (AttributeContent2Object object : metadata2ObjectRepository.findByObjectUuidAndObjectType(uuid, resource)) {
            if (object.getAttributeContent().getAttributeDefinition().getConnectorUuid() == null || object.getAttributeContent().getAttributeDefinition().getConnectorUuid().equals(connectorUuid)) {
                MetadataAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition(MetadataAttribute.class);
                attribute.setContent(object.getAttributeContent().getAttributeContent());
                metadata.add(attribute);
            }
        }
        return metadata;
    }

    @Override
    public List<MetadataAttribute> getMetadata(UUID connectorUuid, UUID uuid, Resource resource, UUID sourceObjectUuid, Resource sourceObjectResource) {
        List<MetadataAttribute> metadata = new ArrayList<>();
        for (AttributeContent2Object object : metadata2ObjectRepository.findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(uuid, resource, sourceObjectUuid, sourceObjectResource)) {
            if (object.getAttributeContent().getAttributeDefinition().getConnectorUuid() == null || object.getAttributeContent().getAttributeDefinition().getConnectorUuid().equals(connectorUuid)) {
                MetadataAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition(MetadataAttribute.class);
                attribute.setContent(object.getAttributeContent().getAttributeContent());
                metadata.add(attribute);
            }
        }
        return metadata;
    }

    @Override
    public List<MetadataAttribute> getMetadataWithSourceForCertificateRenewal(UUID connectorUuid, UUID uuid, Resource resource, UUID sourceObjectUuid, Resource sourceObjectResource) {
        List<MetadataAttribute> metadata = new ArrayList<>();
        for (AttributeContent2Object object : metadata2ObjectRepository.findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(uuid, resource, sourceObjectUuid, sourceObjectResource)) {
            AttributeDefinition definition = object.getAttributeContent().getAttributeDefinition();
            if (definition.getType().equals(AttributeType.META) && (definition.getConnectorUuid() == null || definition.getConnectorUuid().equals(connectorUuid))) {
                MetadataAttribute attribute = definition.getAttributeDefinition(MetadataAttribute.class);
                attribute.setContent(object.getAttributeContent().getAttributeContent());
                metadata.add(attribute);
            }
        }
        return metadata;
    }

    @Override
    public List<MetadataResponseDto> getFullMetadata(UUID uuid, Resource resource, UUID sourceObjectUuid, Resource sourceObjectResource) {
        return getFullMetadata(metadata2ObjectRepository.findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(uuid, resource, sourceObjectUuid, sourceObjectResource));
    }

    @Override
    public List<MetadataResponseDto> getFullMetadataWithNullResource(UUID uuid, Resource resource, List<Resource> sourceObjectResources) {
        List<MetadataResponseDto> metadata = new ArrayList<>();
        for (Resource sourceObjectResource : sourceObjectResources) {
            metadata.addAll(getFullMetadata(metadata2ObjectRepository.findByObjectUuidAndObjectTypeAndSourceObjectType(uuid, resource, sourceObjectResource)));
        }
        metadata.addAll(getFullMetadata(uuid, resource, null, null));
        return metadata;
    }

    @Override
    public List<MetadataResponseDto> getFullMetadata(UUID uuid, Resource resource) {
        return getFullMetadata(metadata2ObjectRepository.findByObjectUuidAndObjectType(uuid, resource));
    }

    private List<MetadataResponseDto> getFullMetadata(List<AttributeContent2Object> iterables) {
        List<MetadataResponseDto> metadataResponses = new ArrayList<>();
        Map<String, HashMap<String, ResponseMetadataDto>> metadata = new HashMap<>();
        for (AttributeContent2Object object : iterables) {
            if (object.getAttributeContent().getAttributeDefinition().getType().equals(AttributeType.META)) {
                List<BaseAttributeContent> deserializedContent = object.getAttributeContent().getAttributeContent();
                if (deserializedContent.stream().filter(e -> e.getData() != null || e.getReference() != null).collect(Collectors.toList()).size() == 0) {
                    continue;
                }
                MetadataAttribute attribute = object.getAttributeContent().getAttributeDefinition().getAttributeDefinition(MetadataAttribute.class);
                ResponseMetadataDto responseMetadataDto = new ResponseMetadataDto();
                responseMetadataDto.setType(AttributeType.META);
                responseMetadataDto.setContentType(attribute.getContentType());
                if (attribute.getProperties() != null) {
                    responseMetadataDto.setLabel(attribute.getProperties().getLabel());
                }
                responseMetadataDto.setContent(object.getAttributeContent().getAttributeContent());
                responseMetadataDto.setName(attribute.getName());
                responseMetadataDto.setUuid(attribute.getUuid());
                responseMetadataDto.setSourceObjectType(object.getSourceObjectType() != null ? object.getSourceObjectType().getCode() : null);
                responseMetadataDto.setSourceObjects(object.getSourceObjectType() != null ? List.of(new NameAndUuidDto(object.getSourceObjectUuid().toString(), object.getSourceObjectName())) : List.of());
                String sourceObjectUuid = object.getSourceObjectUuid() != null ? object.getSourceObjectUuid().toString() : "";
                String sourceObjectName = object.getSourceObjectUuid() != null ? object.getSourceObjectUuid().toString() : "";
                String sourceObjectType = object.getSourceObjectType() != null ? object.getSourceObjectType().getCode() : "";
                Connector connector = object.getConnector();
                String key;
                if (connector == null) {
                    key = ":#";
                } else {
                    key = connector.getName() + ":#" + connector.getUuid().toString();
                }
                if (metadata.containsKey(key)) {
                    if (metadata.get(key).containsKey(object.getAttributeContentUuid().toString() + sourceObjectType)) {
                        ResponseMetadataDto tempDto = metadata.get(key).get(object.getAttributeContentUuid().toString() + sourceObjectType);
                        List<NameAndUuidDto> sourceObjectUuids = new ArrayList<>(tempDto.getSourceObjects());
                        sourceObjectUuids.add(new NameAndUuidDto(sourceObjectUuid, sourceObjectName));
                        tempDto.setSourceObjects(sourceObjectUuids);
                    } else {
                        metadata.get(key).put(object.getAttributeContentUuid().toString() + sourceObjectType, responseMetadataDto);
                    }
                } else {
                    HashMap<String, ResponseMetadataDto> collective = new HashMap<>();
                    collective.put(object.getAttributeContentUuid().toString() + sourceObjectType, responseMetadataDto);
                    metadata.put(key, collective);
                }
            }
        }

        for (Map.Entry<String, HashMap<String, ResponseMetadataDto>> entry : metadata.entrySet()) {
            MetadataResponseDto metadataResponse = new MetadataResponseDto();
            if (entry.getKey().equals(":#")) {
                metadataResponse.setConnectorUuid("Unknown");
                metadataResponse.setConnectorName("Unknown");
            } else {
                String[] split = entry.getKey().split(":#");
                metadataResponse.setConnectorName(split[0]);
                metadataResponse.setConnectorUuid(split[1]);
            }
            List<ResponseMetadataDto> allItems = new ArrayList<>();
            entry.getValue().entrySet().forEach(e -> allItems.add(e.getValue()));
            metadataResponse.setItems(allItems);
            metadataResponses.add(metadataResponse);
        }
        return metadataResponses;
    }

    private void createMetadataDefinition(UUID connectorUuid, MetadataAttribute attribute) {
        AttributeDefinition definition = new AttributeDefinition();
        definition.setAttributeDefinition(attribute);
        definition.setAttributeName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setConnectorUuid(connectorUuid);
        definition.setContentType(attribute.getContentType());
        definition.setType(AttributeType.META);
        definition.setGlobal(false);
        metadataDefinitionRepository.save(definition);
    }

    private void createMetadataContent(final String attributeName, final AttributeContentType contentType, final Connector connector, final UUID objectUuid, final UUID attributeUuid, final UUID sourceObjectUuid, final String sourceObjectName, final List<BaseAttributeContent> metadata, final Resource resource, final Resource sourceObjectResource, final MetadataAttributeProperties properties) {
        AttributeDefinition definition = null;
        UUID connectorUuid = connector != null ? connector.getUuid() : null;
        if (properties != null && properties.isGlobal()) {
            definition = metadataDefinitionRepository.findByTypeAndAttributeNameAndGlobalAndContentType(AttributeType.META, attributeName, true, contentType).orElse(null);
        }
        if (definition == null) {
            definition = metadataDefinitionRepository.findByConnectorUuidAndAttributeUuid(connectorUuid, attributeUuid).orElse(null);
        }

        AttributeContent existingContent = null;
        final List<AttributeContent> attributeContentList = metadataContentRepository.findByBaseAttributeContentAndAttributeDefinition(metadata, definition);
        for (final AttributeContent ac : attributeContentList) {
            if (ac.getAttributeContentItems().size() == metadata.size()) {
                existingContent = ac;
            }
        }

        final AttributeContent2Object metadata2Object = new AttributeContent2Object();
        metadata2Object.setObjectUuid(objectUuid);
        metadata2Object.setObjectType(resource);
        metadata2Object.setSourceObjectUuid(sourceObjectUuid);
        metadata2Object.setSourceObjectName(sourceObjectName);
        metadata2Object.setSourceObjectType(sourceObjectResource);
        metadata2Object.setConnector(connector);

        if (existingContent != null) {
            metadata2Object.setAttributeContent(existingContent);
        } else {
            final AttributeContent content = new AttributeContent();
            content.addAttributeContent(metadata);
            content.setAttributeDefinition(definition);
            metadataContentRepository.save(content);
            metadata2Object.setAttributeContent(content);
        }
        metadata2ObjectRepository.save(metadata2Object);
    }
}

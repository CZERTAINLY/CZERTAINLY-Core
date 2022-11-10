package com.czertainly.core.service.impl;

import com.czertainly.api.model.common.attribute.v2.InfoAttribute;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.core.dao.entity.Metadata2Object;
import com.czertainly.core.dao.entity.MetadataContent;
import com.czertainly.core.dao.entity.MetadataDefinition;
import com.czertainly.core.dao.repository.Metadata2ObjectRepository;
import com.czertainly.core.dao.repository.MetadataContentRepository;
import com.czertainly.core.dao.repository.MetadataDefinitionRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.MetadataService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MetadataServiceImplementation implements MetadataService {

    private MetadataDefinitionRepository metadataDefinitionRepository;
    private MetadataContentRepository metadataContentRepository;
    private Metadata2ObjectRepository metadata2ObjectRepository;

    @Autowired
    public void setMetadataDefinitionRepository(MetadataDefinitionRepository metadataDefinitionRepository) {
        this.metadataDefinitionRepository = metadataDefinitionRepository;
    }

    @Autowired
    public void setMetadataContentRepository(MetadataContentRepository metadataContentRepository) {
        this.metadataContentRepository = metadataContentRepository;
    }

    @Autowired
    public void setMetadata2ObjectRepository(Metadata2ObjectRepository metadata2ObjectRepository) {
        this.metadata2ObjectRepository = metadata2ObjectRepository;
    }

    @Override
    public void createMetadataDefinitions(UUID connectorUuid, List<InfoAttribute> metadataDefinitions) {
        if(metadataDefinitions == null) {
            return;
        }
        for (InfoAttribute infoAttribute : metadataDefinitions) {
            if (!metadataDefinitionRepository.existsByConnectorUuidAndAttributeUuid(connectorUuid, UUID.fromString(infoAttribute.getUuid()))) {
                createMetadataDefinition(connectorUuid, infoAttribute);
            }
        }
    }

    @Override
    public void createMetadata(UUID connectorUuid, UUID objectUuid, UUID sourceObjectUuid, List<InfoAttribute> metadata, Resource resource, Resource sourceObjectResource) {
        if(metadata == null) {
            return;
        }
        for (InfoAttribute infoAttribute : metadata) {
            createMetadataContent(connectorUuid, objectUuid, UUID.fromString(infoAttribute.getUuid()), sourceObjectUuid, infoAttribute.getContent(), resource, sourceObjectResource);
        }
    }

    @Override
    public List<InfoAttribute> getMetadata(UUID uuid, Resource resource) {
        List<InfoAttribute> metadata = new ArrayList<>();
        for(Metadata2Object object: metadata2ObjectRepository.findByObjectUuidAndObjectType(uuid, resource)){
            InfoAttribute attribute = object.getMetadataContent().getMetadataDefinition().getAttributeDefinition();
            attribute.setContent(object.getMetadataContent().getAttributeContent(BaseAttributeContent.class));
            metadata.add(attribute);
        }
        return metadata;
    }

    private void createMetadataDefinition(UUID connectorUuid, InfoAttribute attribute) {
        MetadataDefinition definition = new MetadataDefinition();
        definition.setAttributeDefinition(attribute);
        definition.setAttributeName(attribute.getName());
        definition.setAttributeUuid(UUID.fromString(attribute.getUuid()));
        definition.setConnectorUuid(connectorUuid);
        metadataDefinitionRepository.save(definition);
    }

    private void createMetadataContent(UUID connectorUuid, UUID objectUuid, UUID attributeUuid, UUID sourceObjectUuid, List<BaseAttributeContent> metadata, Resource resource, Resource sourceObjectResource) {
        String serializedContent = AttributeDefinitionUtils.serializeAttributeContent(metadata);
        MetadataDefinition definition = metadataDefinitionRepository.findByConnectorUuidAndAttributeUuid(connectorUuid, attributeUuid);
        MetadataContent existingContent = metadataContentRepository.findByAttributeContentAndMetadataDefinition(serializedContent, definition).orElse(null);

        Metadata2Object metadata2Object = new Metadata2Object();
        metadata2Object.setObjectUuid(objectUuid);
        metadata2Object.setObjectType(resource);
        metadata2Object.setSourceObjectUuid(sourceObjectUuid);
        metadata2Object.setSourceObjectType(sourceObjectResource);

        if (existingContent != null) {
            metadata2Object.setMetadataContent(existingContent);
        } else {
            MetadataContent content = new MetadataContent();
            content.setAttributeContent(metadata);
            content.setMetadataDefinition(definition);
            metadataContentRepository.save(content);
            metadata2Object.setMetadataContent(content);
        }
        metadata2ObjectRepository.save(metadata2Object);
    }
}

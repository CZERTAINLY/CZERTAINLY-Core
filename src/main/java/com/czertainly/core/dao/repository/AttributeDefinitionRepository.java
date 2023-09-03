package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.core.dao.entity.AttributeDefinition;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeDefinitionRepository extends SecurityFilterRepository<AttributeDefinition, String> {
    Boolean existsByConnectorUuidAndAttributeUuidAndTypeAndContentType(UUID connectorUuid, UUID attributeUuid, AttributeType type, AttributeContentType contentType);

    Optional<AttributeDefinition> findByConnectorUuidAndAttributeUuid(UUID connectorUuid, UUID attributeUuid);

    Optional<AttributeDefinition> findByConnectorUuidAndAttributeNameAndReference(UUID connectorUuid, String attributeName, Boolean reference);

    Optional<AttributeDefinition> findByConnectorUuidAndAttributeNameAndAttributeUuidAndTypeAndContentType(UUID connectorUuid, String attributeName, UUID attributeUuid, AttributeType type, AttributeContentType contentType);

    Boolean existsByTypeAndAttributeName(AttributeType type, String attributeName);

    Optional<AttributeDefinition> findByTypeAndAttributeUuid(AttributeType type, UUID attributeUuid);

    Optional<AttributeDefinition> findByTypeAndAttributeName(AttributeType type, String attributeName);

    Optional<AttributeDefinition> findByTypeAndAttributeNameAndGlobalAndContentType(AttributeType type, String attributeName, Boolean global, AttributeContentType contentType);

    List<AttributeDefinition> findByConnectorUuidAndGlobalAndType(UUID connectorUuid, Boolean global, AttributeType type);

    List<AttributeDefinition> findByGlobalAndType(Boolean global, AttributeType type);

    List<AttributeDefinition> findByType(AttributeType type);

    List<AttributeDefinition> findByTypeAndContentType(AttributeType type, AttributeContentType contentType);

}

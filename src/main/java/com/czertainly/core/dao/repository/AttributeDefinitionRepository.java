package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.core.dao.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, String> {
    Boolean existsByConnectorUuidAndAttributeUuidAndTypeAndContentType(UUID connectorUuid, UUID attributeUuid, AttributeType type, AttributeContentType contentType);

    Optional<AttributeDefinition> findByConnectorUuidAndAttributeUuid(UUID connectorUuid, UUID attributeUuid);

    Optional<AttributeDefinition> findByConnectorUuidAndAttributeNameAndAttributeUuidAndTypeAndContentType(UUID connectorUuid, String attributeName, UUID attributeUuid, AttributeType type, AttributeContentType contentType);
}

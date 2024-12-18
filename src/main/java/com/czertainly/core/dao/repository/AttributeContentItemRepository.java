package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.core.dao.entity.AttributeContentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeContentItemRepository extends JpaRepository<AttributeContentItem, String> {

    AttributeContentItem findByJsonAndAttributeDefinitionUuid(BaseAttributeContent<?> attributeContent, UUID definitionUuid);

    long deleteByAttributeDefinitionUuid(UUID definitionUuid);
    long deleteByAttributeDefinitionTypeAndAttributeDefinitionConnectorUuid(AttributeType attributeType, UUID connectorUuid);

}

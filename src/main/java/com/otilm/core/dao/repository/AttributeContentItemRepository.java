package com.otilm.core.dao.repository;

import com.otilm.api.model.common.attribute.common.AttributeContent;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.core.dao.entity.AttributeContentItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttributeContentItemRepository extends JpaRepository<AttributeContentItem, String> {

    AttributeContentItem findByJsonAndAttributeDefinitionUuid(AttributeContent attributeContent, UUID definitionUuid);

    List<AttributeContentItem> findByAttributeDefinitionUuid(UUID definitionUuid);

    void deleteByAttributeDefinitionUuid(UUID definitionUuid);

    void deleteByAttributeDefinitionTypeAndAttributeDefinitionConnectorUuid(AttributeType attributeType,
            UUID connectorUuid);

}

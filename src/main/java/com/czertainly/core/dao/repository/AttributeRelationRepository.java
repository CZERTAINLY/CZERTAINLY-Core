package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.AttributeRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface AttributeRelationRepository extends JpaRepository<AttributeRelation, String> {
    List<AttributeRelation> findByAttributeDefinitionUuidAndResource(UUID attributeDefinitionUuid, Resource resource);

    List<AttributeRelation> findByAttributeDefinitionUuid(UUID attributeDefinitionUuid);

    Boolean existsByAttributeDefinitionUuidAndResource(UUID attributeDefinitionUuid, Resource resource);

    List<AttributeRelation> findByResource(Resource resource);
}
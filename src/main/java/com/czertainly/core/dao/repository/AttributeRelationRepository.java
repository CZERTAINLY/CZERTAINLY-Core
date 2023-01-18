package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public interface AttributeRelationRepository extends JpaRepository<AttributeRelation, String> {
    List<AttributeRelation> findByAttributeDefinitionUuidAndResource(UUID attributeDefinitionUuid, Resource resource);

    List<AttributeRelation> findByAttributeDefinitionUuid(UUID attributeDefinitionUuid);

    Boolean existsByAttributeDefinitionUuidAndResource(UUID attributeDefinitionUuid, Resource resource);

    List<AttributeRelation> findByResource(Resource resource);
}

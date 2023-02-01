package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeContent2Object;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface AttributeContent2ObjectRepository extends JpaRepository<AttributeContent2Object, String> {

    List<AttributeContent2Object> findByObjectUuidAndObjectType(UUID uuid, Resource resource);

    List<AttributeContent2Object> findByObjectUuidAndObjectTypeAndAttributeContentAttributeDefinitionUuid(UUID uuid, Resource resource, UUID attributeDefinitionUuid);

    List<AttributeContent2Object> findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(UUID uuid, Resource resource, UUID sourceObjectUUid, Resource sourceObjectType);

    List<AttributeContent2Object> findByAttributeContent(AttributeContent attributeContent);

     long countByAttributeContent(AttributeContent attributeContent);

    List<AttributeContent2Object> findByObjectUuidAndObjectTypeAndSourceObjectType(UUID uuid, Resource resource, Resource sourceObjectResource);
}

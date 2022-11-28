package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeContent2Object;
import com.czertainly.api.model.core.auth.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public interface AttributeContent2ObjectRepository extends JpaRepository<AttributeContent2Object, String> {

    List<AttributeContent2Object> findByObjectUuidAndObjectType(UUID uuid, Resource resource);

    List<AttributeContent2Object> findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(UUID uuid, Resource resource, UUID sourceObjectUUid, Resource sourceObjectType);

    List<AttributeContent2Object> findByAttributeContent(AttributeContent attributeContent);
}

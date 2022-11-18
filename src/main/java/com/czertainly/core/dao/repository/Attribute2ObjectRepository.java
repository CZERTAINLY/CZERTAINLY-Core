package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Attribute2Object;
import com.czertainly.core.model.auth.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public interface Attribute2ObjectRepository extends JpaRepository<Attribute2Object, String> {

    List<Attribute2Object> findByObjectUuidAndObjectType(UUID uuid, Resource resource);

    List<Attribute2Object> findByObjectUuidAndObjectTypeAndSourceObjectUuidAndSourceObjectType(UUID uuid, Resource resource, UUID sourceObjectUUid, Resource sourceObjectType);
}

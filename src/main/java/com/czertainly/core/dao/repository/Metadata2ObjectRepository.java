package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Metadata2Object;
import com.czertainly.core.dao.entity.MetadataDefinition;
import com.czertainly.core.model.auth.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public interface Metadata2ObjectRepository extends JpaRepository<Metadata2Object, String> {

    List<Metadata2Object> findByObjectUuidAndObjectType(UUID uuid, Resource resource);
}

package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.GroupAssociation;
import com.czertainly.core.dao.entity.ResourceObjectAssociation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GroupAssociationRepository extends JpaRepository<GroupAssociation, UUID> {
    boolean existsByResourceAndObjectUuidAndGroupUuid(Resource resource, UUID objectUuid, UUID groupUuid);
    long deleteByGroupUuid(UUID groupUuid);
    long deleteByResourceAndObjectUuid(Resource resource, UUID objectUuid);
    long deleteByResourceAndObjectUuidAndGroupUuid(Resource resource, UUID objectUuid, UUID groupUuid);

}

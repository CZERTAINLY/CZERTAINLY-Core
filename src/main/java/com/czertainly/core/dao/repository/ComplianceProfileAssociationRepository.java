package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.ComplianceProfileAssociation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplianceProfileAssociationRepository extends SecurityFilterRepository<ComplianceProfileAssociation, UUID> {

    List<ComplianceProfileAssociation> findByComplianceProfileUuidAndResource(UUID complianceProfileUuid, Resource resource);

    @EntityGraph(attributePaths = {"complianceProfile"})
    List<ComplianceProfileAssociation> findByResourceAndObjectUuid(Resource resource, UUID associationObjectUuid);

    long deleteByComplianceProfileUuid(UUID complianceProfileUuid);

    boolean existsByComplianceProfileUuidAndResourceAndObjectUuid(UUID complianceProfileUuid, Resource resource, UUID associationObjectUuid);

    long deleteByComplianceProfileUuidAndResourceAndObjectUuid(UUID complianceProfileUuid, Resource resource, UUID associationObjectUuid);

}

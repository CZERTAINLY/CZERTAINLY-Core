package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceProfileAssociation;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ComplianceProfileAssociationRepository extends SecurityFilterRepository<ComplianceProfileAssociation, UUID> {

    long deleteByComplianceProfileUuid(UUID complianceProfileUuid);

}

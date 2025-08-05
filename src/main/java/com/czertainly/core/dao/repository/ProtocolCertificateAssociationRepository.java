package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ProtocolCertificateAssociation;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProtocolCertificateAssociationRepository extends SecurityFilterRepository<ProtocolCertificateAssociation, UUID>{
}

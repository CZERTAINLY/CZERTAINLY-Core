package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ProtocolCertificateAssociations;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProtocolCertificateAssociationsRepository extends SecurityFilterRepository<ProtocolCertificateAssociations, UUID>{
}

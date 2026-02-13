package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateLocation;
import com.czertainly.core.dao.entity.CertificateLocationId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CertificateLocationRepository extends SecurityFilterRepository<CertificateLocation, CertificateLocationId> {
    List<CertificateLocation> findByCertificateUuidIn(List<UUID> certificateUuids);
}

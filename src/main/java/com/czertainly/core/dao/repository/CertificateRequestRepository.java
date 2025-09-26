package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateRequestEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRequestRepository extends SecurityFilterRepository<CertificateRequestEntity, UUID> {

    Optional<CertificateRequestEntity> findByUuidIn(final List<UUID> uuids);

    Optional<CertificateRequestEntity> findByFingerprint(final String fingerprint);
}

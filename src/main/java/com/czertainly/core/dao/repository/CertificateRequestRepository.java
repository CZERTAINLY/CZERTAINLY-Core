package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateRequestRepository extends JpaRepository<CertificateRequestEntity, Long> {

    Optional<CertificateRequestEntity> findByFingerprint(final String fingerprint);
}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateRequestRepository extends JpaRepository<CertificateRequest, Long> {

    Optional<CertificateRequest> findByFingerprint(final String fingerprint);
}

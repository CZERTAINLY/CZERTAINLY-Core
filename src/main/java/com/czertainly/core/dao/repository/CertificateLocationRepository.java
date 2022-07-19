package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateLocation;
import com.czertainly.core.dao.entity.CertificateLocationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CertificateLocationRepository extends JpaRepository<CertificateLocation, CertificateLocationId> {
}

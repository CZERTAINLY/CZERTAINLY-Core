package com.czertainly.core.dao.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.DiscoveryCertificate;
import com.czertainly.core.dao.entity.DiscoveryHistory;

@Repository
@Transactional
public interface DiscoveryCertificateRepository extends JpaRepository<DiscoveryCertificate, Long> {
    Optional<DiscoveryCertificate> findByUuid(String uuid);
    List<DiscoveryCertificate> findByDiscovery(DiscoveryHistory history);
	List<DiscoveryCertificate> findByCertificateContent(CertificateContent certificateContent);
}

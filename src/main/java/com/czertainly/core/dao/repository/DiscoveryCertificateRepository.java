package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.DiscoveryCertificate;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface DiscoveryCertificateRepository extends SecurityFilterRepository<DiscoveryCertificate, Long> {
    Optional<DiscoveryCertificate> findByUuid(String uuid);
    List<DiscoveryCertificate> findByDiscovery(DiscoveryHistory history);
	List<DiscoveryCertificate> findByCertificateContent(CertificateContent certificateContent);
}

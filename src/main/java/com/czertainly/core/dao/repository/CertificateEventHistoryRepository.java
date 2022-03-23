package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEventHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateEventHistoryRepository extends JpaRepository<CertificateEventHistory, Long> {
    List<CertificateEventHistory> findByCertificateOrderByCreatedDesc(Certificate certificate);
}

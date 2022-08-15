package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEventHistory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateEventHistoryRepository extends SecurityFilterRepository<CertificateEventHistory, Long> {
    List<CertificateEventHistory> findByCertificateOrderByCreatedDesc(Certificate certificate);
}

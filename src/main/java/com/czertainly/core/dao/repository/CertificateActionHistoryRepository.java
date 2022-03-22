package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateActionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateActionHistoryRepository extends JpaRepository<CertificateActionHistory, Long> {
    List<CertificateActionHistory> findByCertificateOrderByCreatedDesc(Certificate certificate);
}

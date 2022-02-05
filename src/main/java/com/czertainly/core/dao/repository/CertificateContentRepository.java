package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;

@Repository
@Transactional
public interface CertificateContentRepository extends JpaRepository<CertificateContent, Long> {

    CertificateContent findByFingerprint(String thumbprint);
    CertificateContent findByContent(String content);
}

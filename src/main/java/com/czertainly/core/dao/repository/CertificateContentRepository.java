package com.czertainly.core.dao.repository;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.CertificateContent;

@Repository
@Transactional
public interface CertificateContentRepository extends JpaRepository<CertificateContent, Long> {

    CertificateContent findByFingerprint(String thumbprint);
}

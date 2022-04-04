package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface CertificateContentRepository extends JpaRepository<CertificateContent, Long> {

    CertificateContent findByFingerprint(String thumbprint);
    CertificateContent findByContent(String content);

    @Query("SELECT c FROM CertificateContent c " +
            "LEFT JOIN Certificate t1 ON c.id= t1.certificateContent " +
            "LEFT JOIN DiscoveryCertificate t2 ON c.id = t2.certificateContent " +
            "WHERE t1.id IS NULL AND t2.id IS null")
    List<CertificateContent> findCertificateContentNotUsed();

}

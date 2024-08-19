package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateContent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateContentRepository extends SecurityFilterRepository<CertificateContent, Long> {

    CertificateContent findByFingerprint(String thumbprint);
    CertificateContent findByContent(String content);

    @Query("SELECT c FROM CertificateContent c " +
            "LEFT JOIN Certificate t1 ON c.id= t1.certificateContentId " +
            "LEFT JOIN DiscoveryCertificate t2 ON c.id = t2.certificateContentId " +
            "WHERE t1 IS NULL AND t2 IS NULL")
    List<CertificateContent> findCertificateContentNotUsed();

    @Modifying
    @Query("""
            DELETE FROM CertificateContent cc WHERE
            	NOT EXISTS (SELECT 1 FROM Certificate c WHERE c.certificateContentId = cc.id)
            	AND
            	NOT EXISTS (SELECT 1 FROM DiscoveryCertificate dc WHERE dc.certificateContentId = cc.id)
            """)
    int deleteUnusedCertificateContents();

}

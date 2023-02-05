package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.custom.CustomCertificateRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface CertificateRepository extends SecurityFilterRepository<Certificate, Long>, CustomCertificateRepository {

    Optional<Certificate> findByUuid(UUID uuid);

    Optional<Certificate> findBySerialNumberIgnoreCase(String serialNumber);

    Certificate findByCertificateContent(CertificateContent certificateContent);

    Optional<Certificate> findByFingerprint(String fingerprint);

    List<Certificate> findBySubjectDn(String subjectDn);

    List<Certificate> findAllByIssuerSerialNumber(String issuerSerialNumber);

    List<Certificate> findByStatus(CertificateStatus status);

    List<Certificate> findByRaProfile(RaProfile raProfile);

    List<Certificate> findByGroup(Group group);

    List<Certificate> findByKeyUuid(UUID keyUuid);

    @Query("SELECT DISTINCT signatureAlgorithm FROM Certificate")
    List<String> findDistinctSignatureAlgorithm();


    @Query("SELECT DISTINCT keySize FROM Certificate")
    List<Integer> findDistinctKeySize();

    @Query("SELECT DISTINCT keyUsage FROM Certificate")
    List<String> findDistinctKeyUsage();

    @Query("SELECT DISTINCT publicKeyAlgorithm FROM Certificate")
    List<String> findDistinctPublicKeyAlgorithm();


    @Modifying
    @Query("delete from Certificate u where u.uuid in ?1")
    void deleteCertificateWithIds(List<String> uuids);

    Optional<Certificate> findByUserUuid(UUID userUuid);

    List<Certificate> findByPublicKeyFingerprint(String fingerprint);

    List<Certificate> findAllByStatusValidationTimestampNullOrStatusValidationTimestampBefore(LocalDateTime statusValidationTimestamp, Pageable pageable);

    @Query("SELECT COUNT(*) FROM Certificate c WHERE c.status NOT IN :skipStatuses")
    long countCertificatesToCheckStatus(@Param("skipStatuses") List<CertificateStatus> skipStatuses);

    @Query("SELECT c, cc.content FROM Certificate c " +
            "JOIN CertificateContent cc ON cc.id = c.certificateContentId " +
            "WHERE c.status NOT IN :skipStatuses " +
            "AND (c.statusValidationTimestamp IS NULL OR c.statusValidationTimestamp <= :statusValidityEndTimestamp) " +
            "ORDER BY c.created ASC")
    List<Certificate> findCertificatesToCheckStatus(@Param("statusValidityEndTimestamp") LocalDateTime statusValidityEndTimestamp,
                                                    @Param("skipStatuses") List<CertificateStatus> skipStatuses,
                                                    Pageable pageable);
}

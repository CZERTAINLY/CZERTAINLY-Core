package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.custom.CustomCertificateRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends SecurityFilterRepository<Certificate, UUID>, CustomCertificateRepository {

    @EntityGraph(attributePaths = {"certificateContent"})
    Optional<Certificate> findByUuid(UUID uuid);

    List<Certificate> findAllByUuidIn(List<UUID> uuids);

    Certificate findFirstByUuidIn(List<UUID> uuids);

    @EntityGraph(attributePaths = {"certificateContent", "key", "key.items", "groups", "owner", "altKey", "altKey.items", "raProfile"})
    Optional<Certificate> findWithAssociationsByUuid(UUID uuid);

    Optional<Certificate> findBySerialNumberIgnoreCase(String serialNumber);

    Certificate findByCertificateContent(CertificateContent certificateContent);

    Optional<Certificate> findByFingerprint(String fingerprint);

    List<Certificate> findByRaProfile(RaProfile raProfile);

    List<Certificate> findByKeyUuid(UUID keyUuid);
    List<Certificate> findByAltKeyUuid(UUID altKeyUuid);

    List<Certificate> findBySourceCertificateUuid(UUID sourceCertificateUuid);

    @Query("SELECT DISTINCT signatureAlgorithm FROM Certificate")
    List<String> findDistinctSignatureAlgorithm();

    @Query("SELECT DISTINCT altSignatureAlgorithm FROM Certificate")
    List<String> findDistinctAltSignatureAlgorithm();

    @Query("SELECT DISTINCT keySize FROM Certificate")
    List<Integer> findDistinctKeySize();

    @Query("SELECT DISTINCT altKeySize FROM Certificate")
    List<Integer> findDistinctAltKeySize();

    @Query("SELECT DISTINCT keyUsage FROM Certificate")
    List<String> findDistinctKeyUsage();

    @Query("SELECT DISTINCT publicKeyAlgorithm FROM Certificate")
    List<String> findDistinctPublicKeyAlgorithm();

    @Query("SELECT DISTINCT altPublicKeyAlgorithm FROM Certificate")
    List<String> findDistinctAltPublicKeyAlgorithm();

    Optional<Certificate> findByUserUuid(UUID userUuid);

    List<Certificate> findByPublicKeyFingerprint(String fingerprint);

    @Query("""
             SELECT COUNT(*) FROM Certificate c LEFT JOIN c.raProfile rp
             WHERE c.certificateContentId IS NOT NULL AND c.validationStatus NOT IN :skipStatuses
             AND ((rp.validationEnabled is NULL AND :platformEnabled = true) OR (rp.validationEnabled = true))
            """)
    long countCertificatesToCheckStatus(@Param("skipStatuses") List<CertificateValidationStatus> skipStatuses, @Param("platformEnabled") boolean platformEnabled);


    // Select certificates which have content, and they are not revoked or expired (since these statuses cannot change)
    // Select certificates according to platform settings, this applies to certificates which either do not have RA Profile assigned or certificates which have RA Profile
    // assigned, validation for that RA Profile is null
    // Select certificates which have validation frequency set in RA Profile
    @Query("""
            SELECT c.uuid FROM Certificate c LEFT JOIN c.raProfile rp
                WHERE c.certificateContentId IS NOT NULL AND c.validationStatus NOT IN :skipStatuses
                    AND
                    (
                ((rp.validationEnabled is NULL AND :platformEnabled = true) AND (c.statusValidationTimestamp IS NULL OR c.statusValidationTimestamp <= :statusValidityEndTimestamp))
                    OR
                ((rp.validationEnabled = true) AND (c.statusValidationTimestamp IS NULL OR c.statusValidationTimestamp <= CURRENT_DATE - rp.validationFrequency DAY))
                    ) ORDER BY c.statusValidationTimestamp ASC NULLS FIRST
            """
    )
    List<UUID> findCertificatesToCheckStatus(@Param("statusValidityEndTimestamp") OffsetDateTime statusValidityEndTimestamp,
                                             @Param("skipStatuses") List<CertificateValidationStatus> skipStatuses,
                                             @Param("platformEnabled") boolean platformEnabled,
                                             Pageable pageable);

    List<Certificate> findByComplianceResultContaining(String ruleUuid);

    List<Certificate> findByRaProfileAndComplianceStatusIsNotNull(RaProfile raProfile);

    Optional<Certificate> findBySubjectDnNormalizedAndSerialNumber(String subjectDnNormalized, String serialNumber);

    Optional<Certificate> findByIssuerDnNormalizedAndSerialNumber(String issuerDnNormalized, String serialNumber);

    List<Certificate> findBySubjectDnNormalized(String issuerDnNormalized);

    List<Certificate> findByValidationStatusAndCertificateContentDiscoveryCertificatesDiscoveryUuid(CertificateValidationStatus validationStatus, UUID discoveryUuid);

    List<Certificate> findByValidationStatusAndLocationsLocationUuid(CertificateValidationStatus validationStatus, UUID locationUuid);

    @Modifying
    @Query("UPDATE Certificate c SET c.keyUuid = ?1 WHERE c.uuid IN ?2")
    void setKeyUuid(UUID keyUuid, List<UUID> uuids);

    @Modifying
    @Query("UPDATE Certificate c SET c.altKeyUuid = ?1, c.hybridCertificate = true WHERE c.uuid IN ?2")
    void setAltKeyUuidAndHybridCertificate(UUID keyUuid, List<UUID> uuids);

    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}certificate (
            uuid, i_author, i_cre, i_upd, ra_profile_uuid,certificate_content_id,
            certificate_request_uuid,source_certificate_uuid,issuer_certificate_uuid,certificate_type,
            state,validation_status,certificate_validation_result,status_validation_timestamp,
            compliance_status,compliance_result,common_name,not_after,not_before,
            extended_key_usage,fingerprint,issuer_common_name,issuer_dn,issuer_dn_normalized,
            issuer_serial_number,key_size,key_usage,key_uuid,public_key_algorithm,
            public_key_fingerprint,serial_number,signature_algorithm,subject_alternative_names,
            subject_dn,subject_dn_normalized,subject_type,trusted_ca,user_uuid,hybrid_certificate,alt_signature_algorithm)
            VALUES (
            :#{#cert.uuid}, :#{#cert.author}, :#{#cert.created}, :#{#cert.updated}, :#{#cert.raProfileUuid}, :#{#cert.certificateContentId},
            :#{#cert.certificateRequestUuid}, :#{#cert.sourceCertificateUuid}, :#{#cert.issuerCertificateUuid}, :#{#cert.certificateType.name()},
            :#{#cert.state.name()}, :#{#cert.validationStatus.name()}, :#{#cert.certificateValidationResult}, :#{#cert.statusValidationTimestamp},
            :#{#cert.complianceStatus.name()}, :#{#cert.complianceResult}, :#{#cert.commonName}, :#{#cert.notAfter}, :#{#cert.notBefore},
            :#{#cert.extendedKeyUsage}, :#{#cert.fingerprint}, :#{#cert.issuerCommonName}, :#{#cert.issuerDn}, :#{#cert.issuerDnNormalized},
            :#{#cert.issuerSerialNumber}, :#{#cert.keySize}, :#{#cert.keyUsage}, :#{#cert.keyUuid}, :#{#cert.publicKeyAlgorithm},
            :#{#cert.publicKeyFingerprint}, :#{#cert.serialNumber}, :#{#cert.signatureAlgorithm}, :#{#cert.subjectAlternativeNames},
            :#{#cert.subjectDn}, :#{#cert.subjectDnNormalized}, :#{#cert.subjectType.name()}, :#{#cert.trustedCa}, :#{#cert.userUuid},
            :#{#cert.hybridCertificate}, :#{#cert.altSignatureAlgorithm}
            )
            ON CONFLICT (fingerprint)
            DO NOTHING
            """, nativeQuery = true)
    int insertWithFingerprintConflictResolve(@Param("cert") Certificate certificate);

    @Query("SELECT uuid FROM Certificate WHERE validationStatus = ?#{T(com.czertainly.api.model.core.certificate.CertificateValidationStatus).EXPIRING} AND uuid NOT IN (SELECT sourceCertificateUuid FROM Certificate WHERE sourceCertificateUuid IS NOT NULL)")
    List<UUID> findExpiringCertificatesWithoutRenewal();
}

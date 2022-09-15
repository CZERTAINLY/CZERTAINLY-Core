package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.custom.CustomCertificateRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Date;
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
    List<Certificate> findByGroup(CertificateGroup group);

    @Query("SELECT DISTINCT signatureAlgorithm FROM Certificate")
    List<String> findDistinctSignatureAlgorithm();
    
    @Query("SELECT DISTINCT certificateType FROM Certificate")
    List<CertificateType> findDistinctCertificateType();
    
    @Query("SELECT DISTINCT keySize FROM Certificate")
    List<Integer> findDistinctKeySize();
    
    @Query("SELECT DISTINCT basicConstraints FROM Certificate")
    List<String> findDistinctBasicConstraints();

    @Query("SELECT DISTINCT keyUsage FROM Certificate")
    List<String> findDistinctKeyUsage();
    
    @Query("SELECT DISTINCT status FROM Certificate")
    List<CertificateStatus> findDistinctStatus();

    @Query("SELECT DISTINCT publicKeyAlgorithm FROM Certificate")
    List<String> findDistinctPublicKeyAlgorithm();

    List<Certificate> findAllByOrderByCreatedDesc(Pageable p);

    @Modifying
    @Query("delete from Certificate u where u.uuid in ?1")
    void deleteCertificateWithIds(List<String> uuids);

    /* Stats queries */
    @Query(value = "SELECT c.groupUuid, COUNT(c.groupUuid) FROM Certificate AS c WHERE c.groupUuid IS NOT NULL GROUP BY c.groupUuid")
    List<Object[]> getCertificatesCountByGroup();

    @Query("SELECT g.uuid, g.name FROM CertificateGroup g WHERE g.uuid IN ?1")
    List<Object[]> getGroupNamesWithUuids(List<UUID> uuids);

    @Query(value = "SELECT c.raProfileUuid, COUNT(c.raProfileUuid) FROM Certificate AS c WHERE c.raProfileUuid IS NOT NULL GROUP BY c.raProfileUuid")
    List<Object[]> getCertificatesCountByRaProfile();

    @Query("SELECT p.uuid, p.name FROM RaProfile p WHERE p.uuid IN ?1")
    List<Object[]> getRaProfileNamesWithUuids(List<UUID> uuids);

    @Query(value = "SELECT c.certificateType, COUNT(c.certificateType) FROM Certificate AS c WHERE c.certificateType IS NOT NULL GROUP BY c.certificateType")
    List<Object[]> getCertificatesCountByType();

    @Query(value = "SELECT c.keySize, COUNT(c.keySize) FROM Certificate AS c WHERE c.keySize IS NOT NULL GROUP BY c.keySize")
    List<Object[]> getCertificatesCountByKeySize();

    @Query(value = "SELECT c.basicConstraints, COUNT(c.basicConstraints) FROM Certificate AS c WHERE c.basicConstraints IS NOT NULL GROUP BY c.basicConstraints")
    List<Object[]> getCertificatesCountByBasicConstraints();

    @Query(value = "SELECT c.status, COUNT(c.status) FROM Certificate AS c WHERE c.status IS NOT NULL GROUP BY c.status")
    List<Object[]> getCertificatesCountByStatus();

    @Query(value = "SELECT c.complianceStatus, COUNT(c.complianceStatus) FROM Certificate AS c GROUP BY c.complianceStatus")
    List<Object[]> getCertificatesCountByComplianceStatus();

    @Query("SELECT COUNT(c.id) FROM Certificate AS c WHERE c.notAfter > ?1 AND c.notAfter <= ?2")
    List<Object[]> getCertificatesCountByExpiryDate(Date notAfterFrom, Date notAfterTo);
}

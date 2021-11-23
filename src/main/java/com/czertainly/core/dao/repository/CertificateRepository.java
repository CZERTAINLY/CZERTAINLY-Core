package com.czertainly.core.dao.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import com.czertainly.core.dao.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.czertainly.api.model.discovery.CertificateStatus;
import com.czertainly.api.model.discovery.CertificateType;

@Repository
@Transactional
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByUuid(String uuid);
    Optional<Certificate> findBySerialNumberIgnoreCase(String serialNumber);
    List<Certificate> findDistinctByGroupId(Long group);
    List<Certificate> findDistinctByStatus(CertificateStatus certificateStatus);
    List<Certificate> findDistinctByEntityId(Long entity);
    List<Certificate> findDistinctByRaProfileId(Long raProfile);
    List<Certificate> findByCertificateType(CertificateType certificateType);
    List<Certificate> findByKeySize(Integer keySize);
    List<Certificate> findByBasicConstraints(String basicConstraints);
    List<Certificate> findByNotAfterLessThan(Date notAfter);
    Optional <Certificate> findByCommonName(String commonName);
    Certificate findByCertificateContent(CertificateContent certificateContent);
	Optional<Certificate> findByFingerprint(String fingerprint);
	Optional<Certificate> findBySubjectDn(String subjectDn);
	List<Certificate> findAllByIssuerSerialNumber(String issuerSerialNumber);

    List<Certificate> findByStatus(CertificateStatus status);

    List<Certificate> findByRaProfile(RaProfile raProfile);
    List<Certificate> findByGroup(CertificateGroup group);
    List<Certificate> findByEntity(CertificateEntity entity);
    
    @Query("SELECT DISTINCT certificateType FROM Certificate")
    List<CertificateType> findDistinctCertificateType();
    
    @Query("SELECT DISTINCT keySize FROM Certificate")
    List<Integer> findDistinctKeySize();
    
    @Query("SELECT DISTINCT basicConstraints FROM Certificate")
    List<String> findDistinctBasicConstraints();
    
    @Query("SELECT DISTINCT status FROM Certificate")
    List<CertificateStatus> findDistinctStatus();
}

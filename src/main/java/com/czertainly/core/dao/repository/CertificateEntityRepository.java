package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface CertificateEntityRepository extends JpaRepository<CertificateEntity, Long> {

    Optional<CertificateEntity> findByName(String name);
    Optional<CertificateEntity> findByUuid(String uuid);
    Optional<CertificateEntity> findById(Long id);
}

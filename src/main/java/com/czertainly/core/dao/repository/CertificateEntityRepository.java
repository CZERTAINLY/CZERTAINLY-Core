package com.czertainly.core.dao.repository;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.CertificateEntity;

@Repository
@Transactional
public interface CertificateEntityRepository extends JpaRepository<CertificateEntity, Long> {

    Optional<CertificateEntity> findByName(String name);
    Optional<CertificateEntity> findByUuid(String uuid);
    Optional<CertificateEntity> findById(Long id);
}

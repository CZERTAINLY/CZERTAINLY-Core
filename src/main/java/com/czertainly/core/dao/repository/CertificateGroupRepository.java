package com.czertainly.core.dao.repository;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.CertificateGroup;

@Repository
@Transactional
public interface CertificateGroupRepository extends JpaRepository<CertificateGroup, Long> {

    Optional<CertificateGroup> findByName(String name);
    Optional<CertificateGroup> findByUuid(String uuid);
    Optional<CertificateGroup> findById(Long id);
}

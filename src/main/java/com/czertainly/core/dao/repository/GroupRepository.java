package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface GroupRepository extends JpaRepository<CertificateGroup, Long> {

    Optional<CertificateGroup> findByName(String name);
    Optional<CertificateGroup> findByUuid(String uuid);
    Optional<CertificateGroup> findById(Long id);
}

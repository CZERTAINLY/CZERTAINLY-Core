package com.czertainly.core.dao.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;

@Repository
@Transactional
public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUuid(String uuid);

    Optional<Admin> findBySerialNumber(String serialNumber);

    Optional<Admin> findByUsername(String username);
    
    List<Admin> findByCertificate(Certificate certificate);

    boolean existsByUsername(String username);

}

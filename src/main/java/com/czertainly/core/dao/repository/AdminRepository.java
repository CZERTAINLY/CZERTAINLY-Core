package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Admin;
import com.czertainly.core.dao.entity.Certificate;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface AdminRepository extends SecurityFilterRepository<Admin, Long> {

    Optional<Admin> findByUuid(String uuid);

    Optional<Admin> findBySerialNumber(String serialNumber);

    Optional<Admin> findByUsername(String username);
    
    List<Admin> findByCertificate(Certificate certificate);

    boolean existsByUsername(String username);

}

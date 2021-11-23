package com.czertainly.core.dao.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Client;

@Repository
@Transactional
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByUuid(String uuid);

    Optional<Client> findBySerialNumber(String serialNumber);

    boolean existsByName(String name);
    
    List<Client> findByCertificate(Certificate certificate);
    
    boolean existsByCertificate(Certificate certificate);
}

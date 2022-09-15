package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Client;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface ClientRepository extends SecurityFilterRepository<Client, Long> {

    Optional<Client> findByUuid(UUID uuid);

    Optional<Client> findBySerialNumber(String serialNumber);

    boolean existsByName(String name);
    
    List<Client> findByCertificate(Certificate certificate);
    
    boolean existsByCertificate(Certificate certificate);
}

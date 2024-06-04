package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Crl;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrlRepository extends SecurityFilterRepository<Crl, Long>
{
    Optional<Crl> findByIssuerDnAndSerialNumber(String issuerDn, String serialNumber);

    List<Crl> findByCaCertificateUuid(UUID caCertificateUuid);


}

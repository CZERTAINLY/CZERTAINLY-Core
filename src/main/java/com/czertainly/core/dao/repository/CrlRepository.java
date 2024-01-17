package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Crl;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CrlRepository extends SecurityFilterRepository<Crl, Long>
{
    Optional<Crl> findByIssuerDnAndSerialNumber(String issuerDn, String serialNumber);


}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Crl;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrlRepository extends SecurityFilterRepository<Crl, Long>
{
    Optional<Crl> findByIssuerDnAndSerialNumber(String issuerDn, String serialNumber);

    List<Crl> findByCaCertificateUuid(UUID caCertificateUuid);

    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}crl (
            uuid, ca_certificate_uuid, issuer_dn, serial_number,
            crl_issuer_dn, crl_number, next_update, crl_number_delta, next_update_delta, last_revocation_date)
            VALUES (
            :#{#crl.uuid}, :#{#crl.caCertificateUuid}, :#{#crl.issuerDn}, :#{#crl.serialNumber},
            :#{#crl.crlIssuerDn}, :#{#crl.crlNumber}, :#{#crl.nextUpdate}, :#{#crl.crlNumberDelta}, :#{#crl.nextUpdateDelta}, :#{#crl.lastRevocationDate})
            ON CONFLICT (issuer_dn, serial_number)
            DO NOTHING
            """, nativeQuery = true)
    int insertWithIssuerConflictResolve(@Param("crl") Crl crl);

}

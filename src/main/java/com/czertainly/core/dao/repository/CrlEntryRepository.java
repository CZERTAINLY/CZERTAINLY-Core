package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.authority.CertificateRevocationReason;
import com.czertainly.core.dao.entity.CrlEntry;
import com.czertainly.core.dao.entity.CrlEntryId;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrlEntryRepository extends SecurityFilterRepository<CrlEntry, Long> {

    Optional<CrlEntry> findById(CrlEntryId id);

    void deleteAllByCrlUuid(UUID crlUuid);

    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}crl_entry (crl_uuid,serial_number,revocation_date,revocation_reason)
            VALUES (?1, ?2, ?3, ?4)
            ON CONFLICT (crl_uuid,serial_number)
            DO NOTHING
            """, nativeQuery = true)
    int insertWithIdConflictResolve(UUID crlUuid, String serialNumber, Date revocationDate, String revocationReason);

}

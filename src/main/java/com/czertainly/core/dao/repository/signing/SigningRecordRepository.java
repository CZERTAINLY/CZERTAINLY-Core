package com.czertainly.core.dao.repository.signing;

import com.czertainly.core.dao.entity.signing.SigningRecord;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SigningRecordRepository extends SecurityFilterRepository<SigningRecord, UUID> {
    boolean existsBySigningProfileUuidAndSigningProfileVersion(UUID signingProfileUuid, int version);

    @Modifying
    @Query("UPDATE SigningRecord sr SET sr.signingProfileUuid = NULL WHERE sr.signingProfileUuid = :signingProfileUuid")
    void clearSigningProfileUuid(UUID signingProfileUuid);
}

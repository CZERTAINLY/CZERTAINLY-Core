package com.czertainly.core.dao.repository.signing;

import com.czertainly.core.dao.entity.signing.DigitalSignature;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DigitalSignatureRepository extends SecurityFilterRepository<DigitalSignature, UUID> {
    boolean existsBySigningProfileUuidAndSigningProfileVersion(UUID signingProfileUuid, int version);

    @Modifying
    @Query("UPDATE DigitalSignature ds SET ds.signingProfileUuid = NULL WHERE ds.signingProfileUuid = :signingProfileUuid")
    void clearSigningProfileUuid(UUID signingProfileUuid);
}

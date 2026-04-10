package com.czertainly.core.dao.repository.signing;

import com.czertainly.core.dao.entity.signing.TspProfile;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TspProfileRepository extends SecurityFilterRepository<TspProfile, UUID> {
    @Modifying
    @Query("UPDATE TspProfile tsp SET tsp.defaultSigningProfileUuid = NULL WHERE tsp.defaultSigningProfileUuid = :signingProfileUuid")
    void clearDefaultSigningProfileUuid(UUID signingProfileUuid);

    Optional<TspProfile> findByName(String name);
}

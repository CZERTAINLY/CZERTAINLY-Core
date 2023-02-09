package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface AcmeProfileRepository extends SecurityFilterRepository<AcmeProfile, Long> {
    Optional<AcmeProfile> findByUuid(UUID uuid);

    boolean existsByName(String name);

    AcmeProfile findByName(String name);

    List<AcmeProfile> findByRaProfile(RaProfile raProfile);
}

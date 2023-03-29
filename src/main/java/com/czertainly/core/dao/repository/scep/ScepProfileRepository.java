package com.czertainly.core.dao.repository.scep;

import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.scep.ScepProfile;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface ScepProfileRepository extends SecurityFilterRepository<ScepProfile, Long> {
    Optional<ScepProfile> findByUuid(UUID uuid);

    boolean existsByName(String name);

    Optional<ScepProfile> findByName(String name);

    List<ScepProfile> findByRaProfile(RaProfile raProfile);
}

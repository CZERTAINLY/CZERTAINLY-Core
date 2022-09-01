package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Location;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends SecurityFilterRepository<Location, Long> {

    Optional<Location> findByUuid(UUID uuid);

    Optional<Location> findByName(String name);

    List<Location> findByEnabled(Boolean isEnabled);

    Optional<Location> findByUuidAndEnabledIsTrue(UUID uuid);

}

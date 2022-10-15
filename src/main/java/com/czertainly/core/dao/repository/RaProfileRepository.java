package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RaProfile;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface RaProfileRepository extends SecurityFilterRepository<RaProfile, Long> {

    Optional<RaProfile> findByUuid(UUID uuid);

    Optional<RaProfile> findByName(String name);

    Optional<RaProfile> findByNameAndEnabledIsTrue(String name);

    Optional<RaProfile> findByUuidAndEnabledIsTrue(UUID uuid);

    List<RaProfile> findAllByAcmeProfileUuid(UUID acmeProfileUuid);

    List<RaProfile> findAllByUuidIn(List<UUID>uuids);
}

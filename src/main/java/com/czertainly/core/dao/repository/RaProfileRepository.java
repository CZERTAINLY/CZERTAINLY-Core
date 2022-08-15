package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RaProfile;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface RaProfileRepository extends SecurityFilterRepository<RaProfile, Long> {

    Optional<RaProfile> findByUuid(String uuid);

    Optional<RaProfile> findByName(String name);

    Optional<RaProfile> findByNameAndEnabledIsTrue(String name);

    Optional<RaProfile> findByUuidAndEnabledIsTrue(String uuid);

    List<RaProfile> findAllByAcmeProfileId(Long acmeProfileId);

    List<RaProfile> findAllByUuidIn(List<String>uuids);
}

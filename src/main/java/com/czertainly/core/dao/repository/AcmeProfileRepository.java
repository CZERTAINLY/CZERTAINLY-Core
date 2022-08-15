package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeProfileRepository extends SecurityFilterRepository<AcmeProfile, Long> {
    Optional<AcmeProfile> findByUuid(String uuid);

    boolean existsByName(String name);

    AcmeProfile findByName(String name);

    List<AcmeProfile> findByRaProfile(RaProfile raProfile);
}

package com.czertainly.core.dao.repository.cmp;

import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmpProfileRepository extends SecurityFilterRepository<CmpProfile, Long> {
    Optional<CmpProfile> findByUuid(UUID uuid);

    boolean existsByName(String name);

    Optional<CmpProfile> findByName(String name);

    List<CmpProfile> findByRaProfile(RaProfile raProfile);

}

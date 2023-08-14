package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.TokenProfile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenProfileRepository extends SecurityFilterRepository<TokenProfile, UUID> {

    Optional<TokenProfile> findByUuid(UUID uuid);

    Optional<TokenProfile> findByName(String name);

    Optional<TokenProfile> findByNameAndEnabledIsTrue(String name);

    Optional<TokenProfile> findByUuidAndEnabledIsTrue(UUID uuid);

    List<TokenProfile> findAllByUuidIn(List<UUID> uuids);
}

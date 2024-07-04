package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.TokenInstanceReference;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenInstanceReferenceRepository extends SecurityFilterRepository<TokenInstanceReference, UUID> {

    Optional<TokenInstanceReference> findByUuid(UUID uuid);

    Optional<TokenInstanceReference> findByName(String name);

}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CryptographicKey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptographicKeyRepository extends SecurityFilterRepository<CryptographicKey, UUID> {

    Optional<CryptographicKey> findByUuid(UUID uuid);

    @EntityGraph(attributePaths = {"groups", "owner"})
    Optional<CryptographicKey> findWithAssociationsByUuid(UUID uuid);

    Optional<CryptographicKey> findByName(String name);
}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CryptographicKey;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptographicKeyRepository extends SecurityFilterRepository<CryptographicKey, UUID> {

    Optional<CryptographicKey> findByUuid(UUID uuid);

    Optional<CryptographicKey> findByName(String name);
}

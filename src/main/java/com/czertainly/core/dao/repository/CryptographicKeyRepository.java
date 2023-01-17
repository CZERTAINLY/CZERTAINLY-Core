package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CryptographicKey;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface CryptographicKeyRepository extends SecurityFilterRepository<CryptographicKey, UUID> {

    Optional<CryptographicKey> findByUuid(UUID uuid);

    Optional<CryptographicKey> findByName(String name);
}

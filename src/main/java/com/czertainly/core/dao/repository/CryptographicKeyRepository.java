package com.czertainly.core.dao.repository;

import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.core.dao.entity.CryptographicKey;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface CryptographicKeyRepository extends SecurityFilterRepository<CryptographicKey, UUID> {

    Optional<CryptographicKey> findByUuid(UUID uuid);

    Optional<CryptographicKey> findByName(String name);
}

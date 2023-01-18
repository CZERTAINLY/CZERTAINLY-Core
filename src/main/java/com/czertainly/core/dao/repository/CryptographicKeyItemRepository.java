package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface CryptographicKeyItemRepository extends SecurityFilterRepository<CryptographicKeyItem, UUID> {

    Optional<CryptographicKeyItem> findByUuid(UUID uuid);

    List<CryptographicKeyItem> findByCryptographicKey(CryptographicKey cryptographicKey);

    boolean existsByKeyReferenceUuid(UUID keyReferenceUuid);

    List<CryptographicKeyItem> findByKeyReferenceUuid(UUID keyReferenceUuid);
}

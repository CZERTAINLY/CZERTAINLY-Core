package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptographicKeyItemRepository extends SecurityFilterRepository<CryptographicKeyItem, UUID> {

    Optional<CryptographicKeyItem> findByUuid(UUID uuid);

    Optional<CryptographicKeyItem> findByFingerprint(String fingerprint);

    Optional<CryptographicKeyItem> findByUuidAndKey(UUID uuid, CryptographicKey cryptographicKey);

    List<CryptographicKeyItem> findByKeyReferenceUuid(UUID keyReferenceUuid);

    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}cryptographic_key_item (
                uuid, name, type, key_reference_uuid, key_uuid, key_algorithm, format, key_data,
                state, enabled, length, fingerprint, reason, created_at, updated_at, usage
            ) VALUES (
                :#{#cki.uuid}, :#{#cki.name}, :#{#cki.type.name()}, :#{#cki.keyReferenceUuid}, :#{#cki.keyUuid},
                :#{#cki.keyAlgorithm.name()}, :#{#cki.format?.name() ?: null}, :#{#cki.keyData}, :#{#cki.state.name()}, :#{#cki.enabled},
                :#{#cki.length}, :#{#cki.fingerprint}, :#{#cki.reason?.name() ?: null}, :#{#cki.createdAt},
                :#{#cki.updatedAt}, :#{#cki.usageBitmask}
            ) ON CONFLICT (fingerprint) DO NOTHING
            """, nativeQuery = true)
    void insertWithFingerprintConflictResolve(@Param("cki") CryptographicKeyItem keyItem);
}

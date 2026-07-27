package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.CryptographicKey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptographicKeyRepository extends SecurityFilterRepository<CryptographicKey, UUID> {

    Optional<CryptographicKey> findByUuid(UUID uuid);

    @EntityGraph(attributePaths = {"groups", "owner", "items"})
    Optional<CryptographicKey> findWithAssociationsByUuid(UUID uuid);

    /**
     * Loads the key with everything the signing path dereferences: its token profile, its key items, and the token
     * instance reference used to resolve the connector. Callers that sign outside a transaction must use this finder
     * rather than {@link #findByUuid(UUID)} so the traversal does not depend on open-session-in-view.
     */
    @EntityGraph(attributePaths = {"tokenProfile", "items", "tokenInstanceReference"})
    Optional<CryptographicKey> findWithKeyItemsAndTokenByUuid(UUID uuid);

    Optional<CryptographicKey> findByName(String name);

    @EntityGraph(attributePaths = {"tokenProfile", "items"})
    List<CryptographicKey> findByUuidIn(List<UUID> uuids);

    long countByTokenProfileUuid(UUID tokenProfileUuid);
}

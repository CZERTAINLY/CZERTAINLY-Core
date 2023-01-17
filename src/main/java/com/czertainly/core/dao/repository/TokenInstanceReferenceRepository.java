package com.czertainly.core.dao.repository;

import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface TokenInstanceReferenceRepository extends SecurityFilterRepository<TokenInstanceReference, UUID> {

    Optional<TokenInstanceReference> findByUuid(UUID uuid);

    Optional<TokenInstanceReference> findByName(String name);

    List<TokenInstanceReference> findByStatus(TokenInstanceStatus status);
}

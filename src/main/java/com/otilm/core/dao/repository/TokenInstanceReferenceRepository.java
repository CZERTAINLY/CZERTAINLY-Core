package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.TokenInstanceReference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenInstanceReferenceRepository extends SecurityFilterRepository<TokenInstanceReference, UUID> {

    Optional<TokenInstanceReference> findByUuid(UUID uuid);

    Optional<TokenInstanceReference> findByName(String name);

}

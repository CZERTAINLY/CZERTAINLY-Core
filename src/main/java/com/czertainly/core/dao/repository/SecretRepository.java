package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Secret;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecretRepository extends SecurityFilterRepository<Secret, UUID> {

    Boolean existsByName(String name);

    @Query("SELECT s.name FROM Secret s WHERE s.sourceVaultProfileUuid = :sourceVaultProfileUuid")
    List<String> findAllNamesBySourceVaultProfileUuid(UUID sourceVaultProfileUuid);

    @Query("SELECT s.name FROM Secret s JOIN s.syncVaultProfiles svp JOIN svp.syncProfile vp WHERE vp.uuid = :syncVaultProfileUuid")
    List<String> findAllNamesBySyncVaultProfileUuid(UUID syncVaultProfileUuid);

    @EntityGraph(attributePaths = {"groups", "owner"})
    Optional<Secret> findWithAssociationsByUuid(UUID uuid);
}

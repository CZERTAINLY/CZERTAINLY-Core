package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Credential;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface CredentialRepository extends SecurityFilterRepository<Credential, Long> {

    Optional<Credential> findByUuid(UUID uuid);

    Optional<Credential> findByName(String name);

    List<Credential> findByKindAndEnabledTrue(String kind);


}

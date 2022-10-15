package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeAuthorization;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface AcmeAuthorizationRepository extends SecurityFilterRepository<AcmeAuthorization, Long> {
    Optional<AcmeAuthorization> findByUuid(UUID uuid);
    Optional<AcmeAuthorization> findByAuthorizationId(String authorizationId);
}

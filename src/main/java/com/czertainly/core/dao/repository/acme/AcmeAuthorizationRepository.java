package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeAuthorization;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeAuthorizationRepository extends SecurityFilterRepository<AcmeAuthorization, Long> {
    Optional<AcmeAuthorization> findByUuid(String uuid);
    Optional<AcmeAuthorization> findByAuthorizationId(String authorizationId);
}

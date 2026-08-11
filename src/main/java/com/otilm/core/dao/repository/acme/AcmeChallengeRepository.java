package com.otilm.core.dao.repository.acme;

import com.otilm.core.dao.entity.acme.AcmeChallenge;
import com.otilm.core.dao.repository.SecurityFilterRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface AcmeChallengeRepository extends SecurityFilterRepository<AcmeChallenge, Long> {

    Optional<AcmeChallenge> findByUuid(UUID uuid);

    @EntityGraph(attributePaths = {"authorization"})
    Optional<AcmeChallenge> findByChallengeId(String challengeId);
}

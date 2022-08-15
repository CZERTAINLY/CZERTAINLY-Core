package com.czertainly.core.dao.repository.acme;

import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeChallengeRepository extends SecurityFilterRepository<AcmeChallenge, Long> {
    Optional<AcmeChallenge> findByUuid(String uuid);
    Optional<AcmeChallenge> findByChallengeId(String challengeId);
}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.acme.AcmeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AcmeProfileRepository extends JpaRepository<AcmeProfile, Long> {
    Optional<AcmeProfile> findByUuid(String uuid);

    boolean existsByName(String name);

    AcmeProfile findByName(String name);
}

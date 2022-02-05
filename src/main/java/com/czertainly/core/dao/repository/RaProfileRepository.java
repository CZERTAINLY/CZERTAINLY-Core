package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface RaProfileRepository extends JpaRepository<RaProfile, Long> {

    Optional<RaProfile> findByUuid(String uuid);

    Optional<RaProfile> findByName(String name);

    Optional<RaProfile> findByNameAndEnabledIsTrue(String name);

    Optional<RaProfile> findByUuidAndEnabledIsTrue(String uuid);

    List<RaProfile> findByEnabled(Boolean isEnabled);

    List<RaProfile> findByAcmeProfile(AcmeProfile acmeProfile);
}

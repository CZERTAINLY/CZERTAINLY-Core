package com.czertainly.core.dao.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.RaProfile;

@Repository
@Transactional
public interface RaProfileRepository extends JpaRepository<RaProfile, Long> {

    Optional<RaProfile> findByUuid(String uuid);

    Optional<RaProfile> findByName(String name);

    Optional<RaProfile> findByNameAndEnabledIsTrue(String name);

    List<RaProfile> findByEnabled(Boolean isEnabled);
}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByUuid(String uuid);

    Optional<Location> findByName(String name);

    Optional<Location> findByNameAndEnabledIsTrue(String name);

    Optional<Location> findByUuidAndEnabledIsTrue(String uuid);

    List<Location> findByEnabled(Boolean isEnabled);

}

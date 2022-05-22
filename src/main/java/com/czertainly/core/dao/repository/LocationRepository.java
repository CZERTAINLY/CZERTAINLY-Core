package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByUuid(String uuid);

    Optional<Location> findByName(String name);

    List<Location> findByEnabled(Boolean isEnabled);

}

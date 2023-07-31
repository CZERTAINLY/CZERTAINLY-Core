package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Group;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends SecurityFilterRepository<Group, Long> {

    Optional<Group> findByName(String name);

    Optional<Group> findByUuid(UUID uuid);
}

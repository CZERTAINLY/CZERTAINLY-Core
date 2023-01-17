package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Group;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface GroupRepository extends SecurityFilterRepository<Group, Long> {

    Optional<Group> findByName(String name);

    Optional<Group> findByUuid(UUID uuid);
}

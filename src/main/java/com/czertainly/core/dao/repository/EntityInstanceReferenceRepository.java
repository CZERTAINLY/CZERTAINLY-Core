package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.EntityInstanceReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EntityInstanceReferenceRepository extends JpaRepository<EntityInstanceReference, Long> {

    Optional<EntityInstanceReference> findByUuid(String uuid);

    Optional<EntityInstanceReference> findByName(String name);
}

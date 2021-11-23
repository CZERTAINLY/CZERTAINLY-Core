package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CAInstanceReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface CAInstanceReferenceRepository extends JpaRepository<CAInstanceReference, Long> {

    Optional<CAInstanceReference> findByUuid(String uuid);

    Optional<CAInstanceReference> findByName(String name);
}

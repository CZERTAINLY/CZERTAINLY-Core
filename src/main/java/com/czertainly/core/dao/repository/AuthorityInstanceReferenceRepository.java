package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AuthorityInstanceReferenceRepository extends JpaRepository<AuthorityInstanceReference, Long> {

    Optional<AuthorityInstanceReference> findByUuid(String uuid);

    Optional<AuthorityInstanceReference> findByName(String name);
}

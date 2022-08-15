package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Credential;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface CredentialRepository extends SecurityFilterRepository<Credential, Long> {

    Optional<Credential> findByUuid(String uuid);

    Optional<Credential> findByName(String name);

    List<Credential> findByKindAndEnabledTrue(String kind);


}

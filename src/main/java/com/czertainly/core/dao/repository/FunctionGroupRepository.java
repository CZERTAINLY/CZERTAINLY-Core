package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.FunctionGroup;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface FunctionGroupRepository extends SecurityFilterRepository<FunctionGroup, Long> {

    Optional<FunctionGroup> findByUuid(UUID uuid);

    Optional<FunctionGroup> findByName(String name);

    Optional<FunctionGroup> findByCode(FunctionGroupCode code);
}

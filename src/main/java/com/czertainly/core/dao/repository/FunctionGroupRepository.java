package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.dao.entity.FunctionGroup;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface FunctionGroupRepository extends SecurityFilterRepository<FunctionGroup, Long> {

    Optional<FunctionGroup> findByUuid(String uuid);

    Optional<FunctionGroup> findByName(String name);

    Optional<FunctionGroup> findByCode(FunctionGroupCode code);
}

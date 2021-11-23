package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.FunctionGroup;
import com.czertainly.api.model.connector.FunctionGroupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface FunctionGroupRepository extends JpaRepository<FunctionGroup, Long> {

    Optional<FunctionGroup> findByUuid(String uuid);

    Optional<FunctionGroup> findByName(String name);

    Optional<FunctionGroup> findByCode(FunctionGroupCode code);
}

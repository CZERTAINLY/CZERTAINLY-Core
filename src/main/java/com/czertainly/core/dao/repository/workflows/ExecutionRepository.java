package com.czertainly.core.dao.repository.workflows;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.workflows.Execution;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionRepository extends SecurityFilterRepository<Execution, UUID> {

    boolean existsByName(String name);

    List<Execution> findAllByResource(Resource resource);

}

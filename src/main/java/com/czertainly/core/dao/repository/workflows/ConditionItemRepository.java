package com.czertainly.core.dao.repository.workflows;

import com.czertainly.core.dao.entity.workflows.ConditionItem;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConditionItemRepository extends SecurityFilterRepository<ConditionItem, UUID> {


}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RuleCondition;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleConditionRepository extends SecurityFilterRepository<RuleCondition, Long> {


}

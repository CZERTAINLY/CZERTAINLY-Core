package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RuleAction;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleActionRepository extends SecurityFilterRepository<RuleAction, Long> {

}

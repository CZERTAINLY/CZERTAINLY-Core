package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Rule;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleRepository extends SecurityFilterRepository<Rule, Long> {

}

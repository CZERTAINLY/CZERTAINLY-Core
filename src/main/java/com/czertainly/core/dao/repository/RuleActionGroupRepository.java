package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RuleActionGroup;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleActionGroupRepository extends SecurityFilterRepository<RuleActionGroup, Long> {

}

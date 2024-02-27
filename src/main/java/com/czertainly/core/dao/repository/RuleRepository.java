package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.Rule;
import com.czertainly.core.dao.entity.RuleConditionGroup;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends SecurityFilterRepository<Rule, Long> {

    List<Rule> findAllByResource(Resource resource);


}

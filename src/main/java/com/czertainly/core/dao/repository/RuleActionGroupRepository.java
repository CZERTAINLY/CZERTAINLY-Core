package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.RuleActionGroup;
import com.czertainly.core.dao.entity.RuleConditionGroup;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleActionGroupRepository extends SecurityFilterRepository<RuleActionGroup, Long> {

    List<RuleActionGroup> findAllByResource(Resource resource);

}

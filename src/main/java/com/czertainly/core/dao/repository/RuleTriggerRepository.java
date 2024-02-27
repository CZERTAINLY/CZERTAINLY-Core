package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.RuleTrigger;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleTriggerRepository extends SecurityFilterRepository<RuleTrigger, Long> {

    List<RuleTrigger> findAllByTriggerResource(Resource triggerResource);

    List<RuleTrigger> findAllByResource(Resource resource);

}

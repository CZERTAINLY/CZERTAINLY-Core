package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.RuleTrigger;
import org.apache.catalina.LifecycleState;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleTriggerRepository extends SecurityFilterRepository<RuleTrigger, Long> {

    List<RuleTrigger> findAllByTriggerResourceUuid(UUID triggerResoureUuid);

}

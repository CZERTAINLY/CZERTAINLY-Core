package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.RuleTrigger2Object;

import java.util.List;
import java.util.UUID;

public interface Object2TriggerRepository extends SecurityFilterRepository<RuleTrigger2Object, Long> {

    List<RuleTrigger2Object> findAllByResourceAndObjectUuid(Resource resource, UUID objectUuid);

}

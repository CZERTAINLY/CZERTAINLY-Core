package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.RuleTrigger2Object;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface Object2TriggerRepository extends SecurityFilterRepository<RuleTrigger2Object, Long> {

    List<RuleTrigger2Object> findAllByResourceAndObjectUuidOrderByTriggerOrderAsc(Resource resource, UUID objectUuid);

    long deleteByResourceAndObjectUuid(Resource resource, UUID objectUuid);

}

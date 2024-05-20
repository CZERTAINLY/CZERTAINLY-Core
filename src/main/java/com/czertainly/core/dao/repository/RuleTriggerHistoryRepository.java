package com.czertainly.core.dao.repository;


import com.czertainly.core.dao.entity.RuleTriggerHistory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleTriggerHistoryRepository extends SecurityFilterRepository<RuleTriggerHistory, Long>{

    List<RuleTriggerHistory> findAllByTriggerUuidAndTriggerAssociationUuid(UUID triggerUuid, UUID triggerAssociationUuid);

}

package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.entity.workflows.TriggerHistoryRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TriggerService {

    List<TriggerDto> listTriggers(Resource resource, Resource eventResource);
    TriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException;
    Trigger getRuleTriggerEntity(String triggerUuid) throws NotFoundException;
    TriggerDetailDto createTrigger(TriggerRequestDto request) throws AlreadyExistException, NotFoundException;
    TriggerDetailDto updateTrigger(String triggerUuid, UpdateTriggerRequestDto request) throws NotFoundException;
    void deleteTrigger(String triggerUuid) throws NotFoundException;

    List<TriggerHistoryDto> getTriggerHistory(String triggerUuid, String triggerObjectUuid);
    TriggerHistory createTriggerHistory(LocalDateTime triggeredAt, UUID triggerUuid, UUID triggerAssociationObjectUuid, UUID objectUuid, UUID referenceObjectUuid);
    TriggerHistoryRecord createRuleTriggerHistoryRecord(TriggerHistory triggerHistory, UUID conditionUuid, UUID executionUuid, String message);
}

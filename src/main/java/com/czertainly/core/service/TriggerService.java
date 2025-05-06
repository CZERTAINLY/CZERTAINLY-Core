package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.entity.workflows.TriggerHistoryRecord;

import java.util.List;
import java.util.UUID;

public interface TriggerService {

    List<TriggerDto> listTriggers(Resource resource);
    TriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException;
    Trigger getTriggerEntity(String triggerUuid) throws NotFoundException;
    TriggerDetailDto createTrigger(TriggerRequestDto request) throws AlreadyExistException, NotFoundException;
    TriggerDetailDto updateTrigger(String triggerUuid, UpdateTriggerRequestDto request) throws NotFoundException;
    void deleteTrigger(String triggerUuid) throws NotFoundException;

    void createTriggerAssociations(ResourceEvent event, Resource resource, UUID associationObjectUuid, List<UUID> triggerUuids) throws NotFoundException;
    void deleteTriggerAssociation(Resource resource, UUID associationObjectUuid);

    List<TriggerHistoryDto> getTriggerHistory(String triggerUuid, String associationObjectUuid);
    TriggerHistorySummaryDto getTriggerHistorySummary(String associationObjectUuid) throws NotFoundException;

    TriggerHistory createTriggerHistory(UUID triggerUuid, UUID triggerAssociationUuid, UUID objectUuid, UUID referenceObjectUuid);
    TriggerHistoryRecord createTriggerHistoryRecord(TriggerHistory triggerHistory, UUID conditionUuid, UUID executionUuid, String message);
}

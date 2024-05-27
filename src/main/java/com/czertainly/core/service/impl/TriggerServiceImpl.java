package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.*;
import com.czertainly.core.dao.repository.workflows.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.TriggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class TriggerServiceImpl implements TriggerService {

    private RuleRepository ruleRepository;

    private ActionRepository actionRepository;

    private TriggerRepository triggerRepository;

    private TriggerAssociationRepository triggerAssociationRepository;

    private TriggerHistoryRepository triggerHistoryRepository;

    private TriggerHistoryRecordRepository triggerHistoryRecordRepository;

    @Autowired
    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Autowired
    public void setActionRepository(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Autowired
    public void setTriggerRepository(TriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Autowired
    public void setTriggerAssociationRepository(TriggerAssociationRepository triggerAssociationRepository) {
        this.triggerAssociationRepository = triggerAssociationRepository;
    }

    @Autowired
    public void setTriggerHistoryRepository(TriggerHistoryRepository triggerHistoryRepository) {
        this.triggerHistoryRepository = triggerHistoryRepository;
    }

    @Autowired
    public void setTriggerHistoryRecordRepository(TriggerHistoryRecordRepository triggerHistoryRecordRepository) {
        this.triggerHistoryRecordRepository = triggerHistoryRecordRepository;
    }

    //region Triggers

    @Override
    @ExternalAuthorization(resource = Resource.TRIGGER, action = ResourceAction.LIST)
    public List<TriggerDto> listTriggers(Resource resource, Resource eventResource) {
        List<Trigger> triggers;
        if (resource != null && eventResource != null) {
            triggers = triggerRepository.findAllByResourceAndEventResource(resource, eventResource);
        } else if (resource != null) {
            triggers = triggerRepository.findAllByResource(resource);
        } else if (eventResource != null) {
            triggers = triggerRepository.findAllByEventResource(eventResource);
        } else {
            triggers = triggerRepository.findAll();
        }

        return triggers.stream().map(Trigger::mapToDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.TRIGGER, action = ResourceAction.DETAIL)
    public TriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException {
        return triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(Trigger.class, triggerUuid)).mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.TRIGGER, action = ResourceAction.DETAIL)
    public Trigger getTriggerEntity(String triggerUuid) throws NotFoundException {
        return triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(Trigger.class, triggerUuid));
    }

    @Override
    @ExternalAuthorization(resource = Resource.TRIGGER, action = ResourceAction.CREATE)
    public TriggerDetailDto createTrigger(TriggerRequestDto request) throws AlreadyExistException, NotFoundException {
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }

        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (request.getType() == null) {
            throw new ValidationException("Property trigger type cannot be empty.");
        }

        if (!request.isIgnoreTrigger()) {
            if (request.getActionsUuids().isEmpty()) {
                throw new ValidationException("Trigger that is not ignore trigger must contain at least one action.");
            }
        } else {
            if (!request.getActionsUuids().isEmpty()) {
                throw new ValidationException("Trigger that is ignore trigger cannot have actions.");
            }
        }

        if (triggerRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("Trigger with same name already exists.");
        }

        Trigger trigger = new Trigger();

        trigger.setName(request.getName());
        trigger.setDescription(request.getDescription());
        trigger.setType(request.getType());
        trigger.setResource(request.getResource());
        trigger.setIgnoreTrigger(request.isIgnoreTrigger());
        trigger.setEvent(request.getEvent());
        trigger.setEventResource(request.getEventResource());
        triggerRepository.save(trigger);

        setTriggerRulesAndActions(trigger, request.getRulesUuids(), request.getActionsUuids());

        return trigger.mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.TRIGGER, action = ResourceAction.UPDATE)
    public TriggerDetailDto updateTrigger(String triggerUuid, UpdateTriggerRequestDto request) throws NotFoundException {
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (request.getType() == null) {
            throw new ValidationException("Property trigger type cannot be empty.");
        }

        if (!request.isIgnoreTrigger()) {
            if (request.getActionsUuids().isEmpty()) {
                throw new ValidationException("Trigger that is not ignore trigger must contain at least one action.");
            }
        } else {
            if (!request.getActionsUuids().isEmpty()) {
                throw new ValidationException("Trigger that is ignore trigger cannot have actions.");
            }
        }

        Trigger trigger = triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(Trigger.class, triggerUuid));

        trigger.setDescription(request.getDescription());
        trigger.setType(request.getType());
        trigger.setResource(request.getResource());
        trigger.setIgnoreTrigger(request.isIgnoreTrigger());
        trigger.setEvent(request.getEvent());
        trigger.setEventResource(request.getEventResource());

        setTriggerRulesAndActions(trigger, request.getRulesUuids(), request.getActionsUuids());

        triggerRepository.save(trigger);
        return trigger.mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.TRIGGER, action = ResourceAction.DELETE)
    public void deleteTrigger(String triggerUuid) throws NotFoundException {
        Trigger trigger = triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(Trigger.class, triggerUuid));

        triggerAssociationRepository.deleteByTriggerUuid(trigger.getUuid());
        triggerRepository.delete(trigger);
    }

    private void setTriggerRulesAndActions(Trigger trigger, List<String> rulesUuids, List<String> actionsUuids) throws NotFoundException {
        List<Rule> rules = new ArrayList<>();
        for (String ruleUuid : rulesUuids) {
            Rule rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid)).orElseThrow(() -> new NotFoundException(Rule.class, ruleUuid));
            if (rule.getResource() != trigger.getResource()) {
                throw new ValidationException("Resource of rule with UUID " + ruleUuid + " does not match trigger resource.");
            }
            rules.add(rule);
        }

        List<Action> actions = new ArrayList<>();
        for (String actionUuid : actionsUuids) {
            Action action = actionRepository.findByUuid(SecuredUUID.fromString(actionUuid)).orElseThrow(() -> new NotFoundException(Action.class, actionUuid));
            if (action.getResource() != trigger.getResource()) {
                throw new ValidationException("Resource of action with UUID " + actionUuid + " does not match trigger resource.");
            }
            actions.add(action);
        }

        trigger.setRules(rules);
        trigger.setActions(actions);
    }

    //endregion

    //region Trigger History

    @Override
    public void deleteTriggerAssociation(Resource resource, UUID associationObjectUuid) {
        triggerAssociationRepository.deleteByResourceAndObjectUuid(Resource.DISCOVERY, associationObjectUuid);
        triggerHistoryRepository.deleteByTriggerAssociationObjectUuid(associationObjectUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TRIGGER, action = ResourceAction.DETAIL)
    public List<TriggerHistoryDto> getTriggerHistory(String triggerUuid, String associationObjectUuid) {
        List<TriggerHistory> triggerHistories = triggerHistoryRepository.findAllByTriggerUuidAndTriggerAssociationObjectUuid(UUID.fromString(triggerUuid), UUID.fromString(associationObjectUuid));
        return triggerHistories.stream().map(TriggerHistory::mapToDto).toList();
    }

    @Override
    public TriggerHistorySummaryDto getTriggerHistorySummary(String associationObjectUuid) throws NotFoundException {
        List<TriggerHistory> triggerHistories = triggerHistoryRepository.findByTriggerAssociationObjectUuidOrderByTriggerUuidAscTriggeredAtAsc(UUID.fromString(associationObjectUuid));

        if (triggerHistories.isEmpty()) {
            throw new NotFoundException("Trigger association object", associationObjectUuid);
        }

        Map<UUID, TriggerHistoryObjectSummaryDto> objectsMapping = new HashMap<>();

        TriggerHistorySummaryDto resultDto = new TriggerHistorySummaryDto();

        // set initial summary data
        Trigger trigger = triggerHistories.get(0).getTrigger();
        resultDto.setAssociationResource(trigger.getEventResource() != null ? trigger.getEventResource() : trigger.getResource());
        resultDto.setAssociationObjectUuid(associationObjectUuid);
        resultDto.setObjectsResource(trigger.getResource());

        int objectsMatched = 0;
        int objectsIgnored = 0;
        for (TriggerHistory history : triggerHistories) {
            if (!history.getTriggerUuid().equals(trigger.getUuid())) {
                trigger = history.getTrigger();
            }


            UUID objectUuid = history.getObjectUuid() != null ? history.getObjectUuid() : history.getReferenceObjectUuid();
            TriggerHistoryObjectSummaryDto objectSummaryDto = objectsMapping.get(objectUuid);
            if (objectSummaryDto == null) {
                objectSummaryDto = new TriggerHistoryObjectSummaryDto();
                objectSummaryDto.setObjectUuid(history.getObjectUuid());
                objectSummaryDto.setReferenceObjectUuid(history.getReferenceObjectUuid());
                objectsMapping.put(objectUuid, objectSummaryDto);
            }

            // update match and ignore flags
            if (history.isConditionsMatched()) {
                if (!objectSummaryDto.isMatched()) {
                    objectSummaryDto.setMatched(true);
                    ++objectsMatched;
                }
                if (trigger.isIgnoreTrigger() && !objectSummaryDto.isIgnored()) {
                    objectSummaryDto.setIgnored(true);
                    ++objectsIgnored;
                }
            }

            // add trigger info with records
            TriggerHistoryObjectTriggerSummaryDto objectTriggerSummaryDto = new TriggerHistoryObjectTriggerSummaryDto();
            objectTriggerSummaryDto.setTriggerUuid(trigger.getUuid());
            objectTriggerSummaryDto.setTriggerName(trigger.getName());
            objectTriggerSummaryDto.setTriggeredAt(history.getTriggeredAt());
            objectTriggerSummaryDto.setMessage(history.getMessage());
            objectTriggerSummaryDto.setRecords(history.getRecords().stream().map(TriggerHistoryRecord::mapToDto).toList());
            objectSummaryDto.getTriggers().add(objectTriggerSummaryDto);
        }

        // update numbers of objects and add them to result
        resultDto.setObjectsEvaluated(objectsMapping.size());
        resultDto.setObjectsMatched(objectsMatched);
        resultDto.setObjectsIgnored(objectsIgnored);
        resultDto.setObjects(objectsMapping.values().stream().toList());

        return resultDto;
    }

    @Override
    public TriggerHistory createTriggerHistory(OffsetDateTime triggeredAt, UUID triggerUuid, UUID triggerAssociationObjectUuid, UUID objectUuid, UUID referenceObjectUuid) {
        TriggerHistory triggerHistory = new TriggerHistory();
        triggerHistory.setTriggerUuid(triggerUuid);
        triggerHistory.setTriggerAssociationObjectUuid(triggerAssociationObjectUuid);

        triggerHistory.setObjectUuid(objectUuid);
        triggerHistory.setReferenceObjectUuid(referenceObjectUuid);
        triggerHistory.setTriggeredAt(triggeredAt);

        triggerHistoryRepository.save(triggerHistory);

        return triggerHistory;
    }

    @Override
    public TriggerHistoryRecord createTriggerHistoryRecord(TriggerHistory triggerHistory, UUID conditionUuid, UUID executionUuid, String message) {
        TriggerHistoryRecord triggerHistoryRecord = new TriggerHistoryRecord();

        triggerHistoryRecord.setTriggerHistory(triggerHistory);
        triggerHistoryRecord.setConditionUuid(conditionUuid);
        triggerHistoryRecord.setExecutionUuid(executionUuid);
        triggerHistoryRecord.setMessage(message);
        triggerHistoryRecordRepository.save(triggerHistoryRecord);
        return triggerHistoryRecord;
    }

    //endregion
}

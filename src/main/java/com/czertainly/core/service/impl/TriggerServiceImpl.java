package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.*;
import com.czertainly.core.dao.repository.workflows.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.TriggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    public TriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException {
        return triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(Trigger.class, triggerUuid)).mapToDetailDto();
    }

    @Override
    public Trigger getTriggerEntity(String triggerUuid) throws NotFoundException {
        return triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(Trigger.class, triggerUuid));
    }

    @Override
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
    public List<TriggerHistoryDto> getTriggerHistory(String triggerUuid, String triggerObjectUuid) {
        List<TriggerHistory> triggerHistories = triggerHistoryRepository.findAllByTriggerUuidAndTriggerAssociationObjectUuid(UUID.fromString(triggerUuid), UUID.fromString(triggerObjectUuid));
        return triggerHistories.stream().map(TriggerHistory::mapToDto).toList();
    }

    @Override
    public TriggerHistory createTriggerHistory(LocalDateTime triggeredAt, UUID triggerUuid, UUID triggerAssociationObjectUuid, UUID objectUuid, UUID referenceObjectUuid) {
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

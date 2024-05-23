package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.workflows.*;
import com.czertainly.core.dao.repository.workflows.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.RuleService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class RuleServiceImplOrig implements RuleService {


    private RuleRepository ruleRepository;

    private ConditionRepository conditionGroupRepository;

    private ConditionItemRepository conditionRepository;

    private ActionRepository actionGroupRepository;

    private ExecutionItemRepository actionRepository;

    private TriggerRepository triggerRepository;

    private TriggerHistoryRepository triggerHistoryRepository;

    private TriggerHistoryRecordRepository triggerHistoryRecordRepository;

    @Autowired
    public void setTriggerHistoryRecordRepository(TriggerHistoryRecordRepository triggerHistoryRecordRepository) {
        this.triggerHistoryRecordRepository = triggerHistoryRecordRepository;
    }

    @Autowired
    public void setTriggerHistoryRepository(TriggerHistoryRepository triggerHistoryRepository) {
        this.triggerHistoryRepository = triggerHistoryRepository;
    }


    @Autowired
    public void setTriggerRepository(TriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Autowired
    public void setActionRepository(ExecutionItemRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Autowired
    public void setActionGroupRepository(ActionRepository actionGroupRepository) {
        this.actionGroupRepository = actionGroupRepository;
    }

    @Autowired
    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Autowired
    public void setConditionRepository(ConditionItemRepository conditionRepository) {
        this.conditionRepository = conditionRepository;
    }

    @Autowired
    public void setConditionGroupRepository(ConditionRepository conditionGroupRepository) {
        this.conditionGroupRepository = conditionGroupRepository;
    }

    //region Rules

    @Override
    public List<RuleDto> listRules(Resource resource) {
        if (resource == null) return ruleRepository.findAll().stream().map(Rule::mapToDto).toList();
        return ruleRepository.findAllByResource(resource).stream().map(Rule::mapToDto).toList();
    }

    @Override
    public RuleDetailDto createRule(RuleRequestDto request) {
        return createRuleEntity(request).mapToDetailDto();
    }

    private Rule createRuleEntity(RuleRequestDto request) {
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (ruleRepository.findAllByResource(request.getResource()).stream().anyMatch(rule -> Objects.equals(rule.getName(), request.getName())))
            throw new ValidationException("Rule with this name already exists for this resource.");


        if (request.getConditions().isEmpty() && request.getConditionsUuids().isEmpty())
            throw new ValidationException("Rule has to contain at least one condition or condition group.");

        Rule rule = new Rule();

        List<Condition> conditions = new ArrayList<>();

        for (String conditionGroupUuid : request.getConditionsUuids()) {
            Optional<Condition> ruleConditionGroup = conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid));
            if (ruleConditionGroup.isPresent() && ruleConditionGroup.get().getResource() == request.getResource()) {
                conditions.add(ruleConditionGroup.get());
            } else {
                throw new ValidationException("Condition group with UUID " + conditionGroupUuid + " is either not present or resource of the group does not match rule resource.");
            }
        }

        rule.setConditions(createConditions(request.getConditions(), rule, null));

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setResource(request.getResource());
        rule.setResourceType(request.getResourceType());
        rule.setResourceFormat(request.getResourceFormat());

        rule.setConditions(conditions);

        ruleRepository.save(rule);
        return rule;
    }

    @Override
    public RuleDetailDto getRule(String ruleUuid) throws NotFoundException {
        return getRuleEntity(ruleUuid).mapToDetailDto();
    }

    private Rule getRuleEntity(String ruleUuid) throws NotFoundException {
        return ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid)).orElseThrow(() -> new NotFoundException(Rule.class, ruleUuid));
    }

    @Override
    public RuleDetailDto updateRule(String ruleUuid, UpdateRuleRequestDto request) throws NotFoundException {

        if (request.getConditions().isEmpty() && request.getConditionGroupsUuids().isEmpty())
            throw new ValidationException("Rule has to contain at least one condition or condition group.");


        Rule rule = getRuleEntity(ruleUuid);

        List<Condition> conditions = new ArrayList<>();

        for (String conditionGroupUuid : request.getConditionGroupsUuids()) {
            Optional<Condition> ruleConditionGroup = conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid));
            if (ruleConditionGroup.isPresent() && ruleConditionGroup.get().getResource() == rule.getResource()) {
                conditions.add(ruleConditionGroup.get());
            } else {
                throw new ValidationException("Condition group with UUID " + conditionGroupUuid + " is either not present or resource of the group does not match rule resource.");
            }
        }

        conditionRepository.deleteAll(rule.getConditions());
        rule.setConditions(createConditions(request.getConditions(), rule, null));

        rule.setDescription(request.getDescription());
        rule.setResourceType(request.getResourceType());
        rule.setResourceFormat(request.getResourceFormat());

        rule.setConditions(conditions);

        ruleRepository.save(rule);
        return rule.mapToDetailDto();
    }

    @Override
    public void deleteRule(String ruleUuid) throws NotFoundException {
        ruleRepository.delete(getRuleEntity(ruleUuid));
    }

    @Override
    public List<ConditionDto> listConditions(Resource resource) {
        if (resource == null)
            return conditionGroupRepository.findAll().stream().map(Condition::mapToDto).toList();
        return conditionGroupRepository.findAllByResource(resource).stream().map(Condition::mapToDto).toList();
    }

    @Override
    public ConditionDto createCondition(ConditionRequestDto request) {
        return createConditionGroupEntity(request).mapToDto();
    }

    private Condition createConditionGroupEntity(ConditionRequestDto request) {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot create a condition group without any conditions.");
        }
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (conditionGroupRepository.findAllByResource(request.getResource()).stream().map(Condition::getName).toList().contains(request.getName()))
            throw new ValidationException("Condition group with this name already exists for this resource.");


        Condition conditionGroup = new Condition();
        conditionGroup.setName(request.getName());
        conditionGroup.setDescription(request.getDescription());
        conditionGroup.setResource(request.getResource());
        conditionGroup.setItems(createConditions(request.getItems(), null, conditionGroup));
        conditionGroupRepository.save(conditionGroup);
        return conditionGroup;
    }

    @Override
    public ConditionDto getCondition(String conditionUuid) throws NotFoundException {
        return getConditionGroupEntity(conditionUuid).mapToDto();
    }

    private Condition getConditionGroupEntity(String conditionGroupUuid) throws NotFoundException {
        return conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid)).orElseThrow(() -> new NotFoundException(Condition.class, conditionGroupUuid));
    }

    @Override
    public ConditionDto updateCondition(String conditionUuid, UpdateConditionRequestDto request) throws NotFoundException {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot update a condition group without any conditions.");
        }

        Condition conditionGroup = getConditionGroupEntity(conditionUuid);
        conditionGroup.setDescription(request.getDescription());
        conditionRepository.deleteAll(conditionGroup.getItems());
        conditionGroup.setItems(createConditions(request.getItems(), null, conditionGroup));
        conditionGroupRepository.save(conditionGroup);
        return conditionGroup.mapToDto();
    }

    @Override
    public void deleteCondition(String conditionUuid) throws NotFoundException {
        conditionGroupRepository.delete(getConditionGroupEntity(conditionUuid));
    }

    @Override
    public List<ActionDto> listExecutions(Resource resource) {
        if (resource == null) return actionGroupRepository.findAll().stream().map(Action::mapToDto).toList();
        return actionGroupRepository.findAllByResource(resource).stream().map(Action::mapToDto).toList();
    }

    @Override
    public ActionDto createExecution(ExecutionRequestDto request) {
        return createActionGroupEntity(request).mapToDto();
    }

    private Action createActionGroupEntity(ExecutionRequestDto request) {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot create an action group without any actions.");
        }
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }
        if (actionGroupRepository.findAllByResource(request.getResource()).stream().map(Action::getName).toList().contains(request.getName()))
            throw new ValidationException("Action group with this name already exists for this resource.");

        Action actionGroup = new Action();
        actionGroup.setName(request.getName());
        actionGroup.setDescription(request.getDescription());
        actionGroup.setResource(request.getResource());
        actionGroup.setActions(createActions(request.getItems(), null, actionGroup));
        actionGroupRepository.save(actionGroup);
        return actionGroup;
    }

    @Override
    public ActionDto getExecution(String executionUuid) throws NotFoundException {
        return getActionGroupEntity(executionUuid).mapToDto();
    }

    private Action getActionGroupEntity(String actionGroupUuid) throws NotFoundException {
        return actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid)).orElseThrow(() -> new NotFoundException(Action.class, actionGroupUuid));
    }

    @Override
    public ActionDto updateExecution(String executionUuid, UpdateExecutionRequestDto request) throws NotFoundException {

        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot update an action group without any actions.");
        }

        Action actionGroup = getActionGroupEntity(executionUuid);
        actionGroup.setDescription(request.getDescription());
        actionRepository.deleteAll(actionGroup.getActions());
        actionGroup.setActions(createActions(request.getItems(), null, actionGroup));
        actionGroupRepository.save(actionGroup);
        return actionGroup.mapToDto();
    }

    @Override
    public void deleteExecution(String executionUuid) throws NotFoundException {
        actionGroupRepository.delete(getActionGroupEntity(executionUuid));
    }

    @Override
    public List<TriggerDto> listTriggers(Resource resource, Resource triggerResource) {
        List<Trigger> triggers = triggerRepository.findAll();
        if (triggerResource != null)
            triggers = triggers.stream().filter(trigger -> trigger.getEventResource() == triggerResource).toList();
        if (resource != null)
            triggers = triggers.stream().filter(trigger -> (trigger.getResource() == resource)).toList();
        return triggers.stream().map(Trigger::mapToDto).toList();
    }

    @Override
    public TriggerDetailDto createTrigger(TriggerRequestDto request) {

        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }

        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (request.getType() == null) {
            throw new ValidationException("Property trigger type cannot be empty.");
        }

        if (triggerRepository.findAllByEventResource(request.getEventResource()).stream().anyMatch(trigger -> Objects.equals(trigger.getName(), request.getName())))
            throw new ValidationException("Rule trigger with this name already exists for this trigger resource.");

        if (request.getActions().isEmpty() && request.getActionsUuids().isEmpty())
            throw new ValidationException("Trigger must contain at least one action or action group.");


        Trigger trigger = new Trigger();

        trigger.setName(request.getName());
        trigger.setDescription(request.getDescription());
        trigger.setResource(request.getResource());
        trigger.setEventResource(request.getEventResource() == null ? request.getResource() : request.getEventResource());
        trigger.setType(request.getType());
        trigger.setEvent(request.getEvent());
        triggerRepository.save(trigger);

        List<Rule> rules = new ArrayList<>();

        for (String ruleUuid : request.getRulesUuids()) {
            Optional<Rule> rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid));
            if (rule.isPresent() && rule.get().getResource() == request.getResource()) {
                rules.add(rule.get());
            } else {
                throw new ValidationException("Rule with UUID " + ruleUuid + " is either not present or resource of the rule does not match trigger resource.");
            }
        }

        List<ExecutionItem> actions = createActions(request.getActions(), trigger, null);

        // If there is IGNORE action in actions, do not create any action groups, since it is supposed to be the only action in the trigger
        if (actions.stream().anyMatch(action -> action.getActionType() == ExecutionType.IGNORE) && !request.getActionsUuids().isEmpty())
            throw new ValidationException("Trigger has action of Ignore type, cannot create action groups for such trigger.");
        List<Action> actionGroups = new ArrayList<>();
        for (String actionGroupUuid : request.getActionsUuids()) {
            Optional<Action> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
            if (actionGroup.isPresent() && actionGroup.get().getResource() == request.getResource()) {
                actionGroups.add(actionGroup.get());
            } else {
                throw new ValidationException("Action group with UUID " + actionGroupUuid + " is either not present or resource of the group does not match trigger resource.");
            }
        }

        trigger.setActions(actionGroups);
        trigger.setRules(rules);
        trigger.setActions(actions);
        return trigger.mapToDetailDto();
    }

    @Override
    public TriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException {
        return getRuleTriggerEntity(triggerUuid).mapToDetailDto();
    }

    @Override
    public Trigger getRuleTriggerEntity(String triggerUuid) throws NotFoundException {
        return triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(Trigger.class, triggerUuid));
    }


    @Override
    public TriggerDetailDto updateTrigger(String triggerUuid, UpdateTriggerRequestDto request) throws NotFoundException {


        if (request.getType() == null) {
            throw new ValidationException("Property trigger type cannot be empty.");
        }

        if (request.getActionGroupsUuids().isEmpty() && request.getActions().isEmpty()) {
            throw new ValidationException("Cannot update a trigger without any actions or action groups.");
        }


        Trigger trigger = getRuleTriggerEntity(triggerUuid);

        actionRepository.deleteAll(trigger.getActions());
        trigger.getActions().clear();

        List<Rule> rules = new ArrayList<>();

        for (String ruleUuid : request.getRulesUuids()) {
            Optional<Rule> rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid));
            if (rule.isPresent() && rule.get().getResource() == trigger.getResource()) {
                rules.add(rule.get());
            } else {
                throw new ValidationException("Rule with UUID " + ruleUuid + " is either not present or resource of the rule does not match trigger resource.");
            }
        }

        List<ExecutionItem> actions = createActions(request.getActions(), trigger, null);
        // If there is IGNORE action in actions, do not create any action groups, since it is supposed to be the only action in the trigger
        if (actions.stream().anyMatch(action -> action.getActionType() == ExecutionType.IGNORE) && !request.getActionGroupsUuids().isEmpty())
            throw new ValidationException("Trigger has action of Ignore type, cannot create action groups for such trigger.");
        List<Action> actionGroups = new ArrayList<>();

        for (String actionGroupUuid : request.getActionGroupsUuids()) {
            Optional<Action> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
            if (actionGroup.isPresent() && actionGroup.get().getResource() == trigger.getResource()) {
                actionGroups.add(actionGroup.get());
            } else {
                throw new ValidationException("Action group with UUID " + actionGroupUuid + " is either not present or resource of the group does not match trigger resource.");
            }
        }

        trigger.setActions(actionGroups);
        trigger.setRules(rules);
        trigger.setActions(actions);
        trigger.setType(request.getType());
        trigger.setDescription(request.getDescription());
        if (request.getResource() != null) {
            trigger.setResource(request.getResource());
        }
        if (request.getEventResource() != null) {
            trigger.setEventResource(request.getEventResource());
        }
        if (request.getEvent() != null) {
            trigger.setEvent(request.getEvent());
        }

        triggerRepository.save(trigger);
        return trigger.mapToDetailDto();
    }

    @Override
    public void deleteTrigger(String triggerUuid) throws NotFoundException {
        triggerRepository.delete(getRuleTriggerEntity(triggerUuid));
    }

    @Override
    public List<TriggerHistoryDto> getTriggerHistory(String triggerUuid, String triggerObjectUuid) {
        List<TriggerHistory> triggerHistories = triggerHistoryRepository.findAllByTriggerUuidAndTriggerAssociationUuid(UUID.fromString(triggerUuid), UUID.fromString(triggerObjectUuid));
        return triggerHistories.stream().map(TriggerHistory::mapToDto).toList();
    }

    @Override
    public TriggerHistory createTriggerHistory(LocalDateTime triggeredAt, UUID triggerUuid, UUID triggerAssociationUuid, UUID objectUuid, UUID referenceObjectUuid) {
        TriggerHistory triggerHistory = new TriggerHistory();
        triggerHistory.setTriggeredAt(triggeredAt);
        triggerHistory.setReferenceObjectUuid(referenceObjectUuid);
        triggerHistory.setTriggerUuid(triggerUuid);
        triggerHistory.setTriggerAssociationObjectUuid(triggerAssociationUuid);
        triggerHistory.setObjectUuid(objectUuid);
        triggerHistoryRepository.save(triggerHistory);
        return triggerHistory;
    }

    @Override
    public TriggerHistoryRecord createRuleTriggerHistoryRecord(TriggerHistory triggerHistory, UUID actionUuid, UUID conditionUuid, String message) {
        TriggerHistoryRecord triggerHistoryRecord = new TriggerHistoryRecord();
        triggerHistoryRecord.setTriggerHistory(triggerHistory);
        triggerHistoryRecord.setExecutionUuid(actionUuid);
        triggerHistoryRecord.setConditionUuid(conditionUuid);
        triggerHistoryRecord.setMessage(message);
        triggerHistoryRecordRepository.save(triggerHistoryRecord);
        return triggerHistoryRecord;
    }


    private List<ConditionItem> createConditions(List<ConditionItemRequestDto> conditionRequestDtos, Rule rule, Condition conditionGroup) {
        List<ConditionItem> conditions = new ArrayList<>();
        for (ConditionItemRequestDto conditionRequestDto : conditionRequestDtos) {
            if (conditionRequestDto.getFieldSource() == null || conditionRequestDto.getFieldIdentifier() == null || conditionRequestDto.getOperator() == null)
                throw new ValidationException("Missing field source, field identifier or operator in a condition.");
            ConditionItem condition = new ConditionItem();
            if (rule != null) {
                condition.setRule(rule);
            } else {
                condition.setCondition(conditionGroup);
            }
            condition.setFieldSource(conditionRequestDto.getFieldSource());
            condition.setFieldIdentifier(conditionRequestDto.getFieldIdentifier());
            condition.setOperator(conditionRequestDto.getOperator());
            condition.setValue(conditionRequestDto.getValue());
            conditionRepository.save(condition);
            conditions.add(condition);
        }
        return conditions;
    }

    private List<ExecutionItem> createActions(List<ExecutionItemRequestDto> actionRequestDtos, Trigger trigger, Action actionGroup) {
        List<ExecutionItem> actions = new ArrayList<>();
        for (ExecutionItemRequestDto actionRequestDto : actionRequestDtos) {
            if (actionRequestDto.getActionType() == null)
                throw new ValidationException("Missing action type in an action.");
            // If the Action Type is Ignore, it must be the only action in the list
            if (actionRequestDto.getActionType() == ExecutionType.IGNORE && actionRequestDtos.size() > 1)
                throw new ValidationException("Actions contain action with Action Type Ignore, it must be the only action in the list.");
            ExecutionItem action = new ExecutionItem();
            if (trigger != null) {
                action.setTrigger(trigger);
            } else {
                action.setExecution(actionGroup);
            }
            action.setActionType(actionRequestDto.getActionType());
            action.setFieldSource(actionRequestDto.getFieldSource());
            action.setFieldIdentifier(actionRequestDto.getFieldIdentifier());

            if (action.getFieldSource() != FilterFieldSource.CUSTOM) {
                action.setData(actionRequestDto.getData());
            } else {
                try {
                    AttributeContentType attributeContentType = AttributeContentType.valueOf(actionRequestDto.getFieldIdentifier().substring(actionRequestDto.getFieldIdentifier().indexOf("|") + 1));
                    List<BaseAttributeContent<?>> contentItems = AttributeDefinitionUtils.createAttributeContentFromString(attributeContentType, actionRequestDto.getData() instanceof ArrayList<?> ? (List<String>) actionRequestDto.getData() : List.of(actionRequestDto.getData().toString()));
                    action.setData(contentItems);
                } catch (IllegalArgumentException e) {
                    throw new ValidationException("Unknown content type for custom attribute with field identifier: " + actionRequestDto.getFieldIdentifier());
                }
            }
            actionRepository.save(action);

            actions.add(action);
        }
        return actions;
    }


}

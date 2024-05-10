package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.*;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class RuleServiceImpl implements RuleService {


    private RuleRepository ruleRepository;

    private RuleConditionGroupRepository conditionGroupRepository;

    private RuleConditionRepository conditionRepository;

    private RuleActionGroupRepository actionGroupRepository;

    private RuleActionRepository actionRepository;

    private RuleTriggerRepository triggerRepository;


    @Autowired
    public void setTriggerRepository(RuleTriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Autowired
    public void setActionRepository(RuleActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Autowired
    public void setActionGroupRepository(RuleActionGroupRepository actionGroupRepository) {
        this.actionGroupRepository = actionGroupRepository;
    }

    @Autowired
    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Autowired
    public void setConditionRepository(RuleConditionRepository conditionRepository) {
        this.conditionRepository = conditionRepository;
    }

    @Autowired
    public void setConditionGroupRepository(RuleConditionGroupRepository conditionGroupRepository) {
        this.conditionGroupRepository = conditionGroupRepository;
    }

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


        if (request.getConditions().isEmpty() && request.getConditionGroupsUuids().isEmpty())
            throw new ValidationException("Rule has to contain at least one condition or condition group.");

        Rule rule = new Rule();

        List<RuleConditionGroup> ruleConditionGroups = new ArrayList<>();

        for (String conditionGroupUuid : request.getConditionGroupsUuids()) {
            Optional<RuleConditionGroup> ruleConditionGroup = conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid));
            if (ruleConditionGroup.isPresent() && ruleConditionGroup.get().getResource() == request.getResource()) {
                ruleConditionGroups.add(ruleConditionGroup.get());
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

        rule.setConditionGroups(ruleConditionGroups);

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

        List<RuleConditionGroup> ruleConditionGroups = new ArrayList<>();

        for (String conditionGroupUuid : request.getConditionGroupsUuids()) {
            Optional<RuleConditionGroup> ruleConditionGroup = conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid));
            if (ruleConditionGroup.isPresent() && ruleConditionGroup.get().getResource() == rule.getResource()) {
                ruleConditionGroups.add(ruleConditionGroup.get());
            } else {
                throw new ValidationException("Condition group with UUID " + conditionGroupUuid + " is either not present or resource of the group does not match rule resource.");
            }
        }

        conditionRepository.deleteAll(rule.getConditions());
        rule.setConditions(createConditions(request.getConditions(), rule, null));

        rule.setDescription(request.getDescription());
        rule.setResourceType(request.getResourceType());
        rule.setResourceFormat(request.getResourceFormat());

        rule.setConditionGroups(ruleConditionGroups);

        ruleRepository.save(rule);
        return rule.mapToDetailDto();
    }

    @Override
    public void deleteRule(String ruleUuid) throws NotFoundException {
        ruleRepository.delete(getRuleEntity(ruleUuid));
    }

    @Override
    public List<RuleConditionGroupDto> listConditionGroups(Resource resource) {
        if (resource == null)
            return conditionGroupRepository.findAll().stream().map(RuleConditionGroup::mapToDto).toList();
        return conditionGroupRepository.findAllByResource(resource).stream().map(RuleConditionGroup::mapToDto).toList();
    }

    @Override
    public RuleConditionGroupDto createConditionGroup(RuleConditionGroupRequestDto request) {
        return createConditionGroupEntity(request).mapToDto();
    }

    private RuleConditionGroup createConditionGroupEntity(RuleConditionGroupRequestDto request) {
        if (request.getConditions().isEmpty()) {
            throw new ValidationException("Cannot create a condition group without any conditions.");
        }
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (conditionGroupRepository.findAllByResource(request.getResource()).stream().map(RuleConditionGroup::getName).toList().contains(request.getName()))
            throw new ValidationException("Condition group with this name already exists for this resource.");


        RuleConditionGroup conditionGroup = new RuleConditionGroup();
        conditionGroup.setName(request.getName());
        conditionGroup.setDescription(request.getDescription());
        conditionGroup.setResource(request.getResource());
        conditionGroup.setConditions(createConditions(request.getConditions(), null, conditionGroup));
        conditionGroupRepository.save(conditionGroup);
        return conditionGroup;
    }

    @Override
    public RuleConditionGroupDto getConditionGroup(String conditionGroupUuid) throws NotFoundException {
        return getConditionGroupEntity(conditionGroupUuid).mapToDto();
    }

    private RuleConditionGroup getConditionGroupEntity(String conditionGroupUuid) throws NotFoundException {
        return conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid)).orElseThrow(() -> new NotFoundException(RuleConditionGroup.class, conditionGroupUuid));
    }

    @Override
    public RuleConditionGroupDto updateConditionGroup(String conditionGroupUuid, UpdateRuleConditionGroupRequestDto request) throws NotFoundException {
        if (request.getConditions().isEmpty()) {
            throw new ValidationException("Cannot update a condition group without any conditions.");
        }

        RuleConditionGroup conditionGroup = getConditionGroupEntity(conditionGroupUuid);
        conditionGroup.setDescription(request.getDescription());
        conditionRepository.deleteAll(conditionGroup.getConditions());
        conditionGroup.setConditions(createConditions(request.getConditions(), null, conditionGroup));
        conditionGroupRepository.save(conditionGroup);
        return conditionGroup.mapToDto();
    }

    @Override
    public void deleteConditionGroup(String conditionGroupUuid) throws NotFoundException {
        conditionGroupRepository.delete(getConditionGroupEntity(conditionGroupUuid));
    }

    @Override
    public List<RuleActionGroupDto> listActionGroups(Resource resource) {
        if (resource == null) return actionGroupRepository.findAll().stream().map(RuleActionGroup::mapToDto).toList();
        return actionGroupRepository.findAllByResource(resource).stream().map(RuleActionGroup::mapToDto).toList();
    }

    @Override
    public RuleActionGroupDto createActionGroup(RuleActionGroupRequestDto request) {
        return createActionGroupEntity(request).mapToDto();
    }

    private RuleActionGroup createActionGroupEntity(RuleActionGroupRequestDto request) {
        if (request.getActions().isEmpty()) {
            throw new ValidationException("Cannot create an action group without any actions.");
        }
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }
        if (actionGroupRepository.findAllByResource(request.getResource()).stream().map(RuleActionGroup::getName).toList().contains(request.getName()))
            throw new ValidationException("Action group with this name already exists for this resource.");

        RuleActionGroup actionGroup = new RuleActionGroup();
        actionGroup.setName(request.getName());
        actionGroup.setDescription(request.getDescription());
        actionGroup.setResource(request.getResource());
        actionGroup.setActions(createActions(request.getActions(), null, actionGroup));
        actionGroupRepository.save(actionGroup);
        return actionGroup;
    }

    @Override
    public RuleActionGroupDto getActionGroup(String actionGroupUuid) throws NotFoundException {
        return getActionGroupEntity(actionGroupUuid).mapToDto();
    }

    private RuleActionGroup getActionGroupEntity(String actionGroupUuid) throws NotFoundException {
        return actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid)).orElseThrow(() -> new NotFoundException(RuleActionGroup.class, actionGroupUuid));
    }

    @Override
    public RuleActionGroupDto updateActionGroup(String actionGroupUuid, UpdateRuleActionGroupRequestDto request) throws NotFoundException {

        if (request.getActions().isEmpty()) {
            throw new ValidationException("Cannot update an action group without any actions.");
        }

        RuleActionGroup actionGroup = getActionGroupEntity(actionGroupUuid);
        actionGroup.setDescription(request.getDescription());
        actionRepository.deleteAll(actionGroup.getActions());
        actionGroup.setActions(createActions(request.getActions(), null, actionGroup));
        actionGroupRepository.save(actionGroup);
        return actionGroup.mapToDto();
    }

    @Override
    public void deleteActionGroup(String actionGroupUuid) throws NotFoundException {
        actionGroupRepository.delete(getActionGroupEntity(actionGroupUuid));
    }

    @Override
    public List<RuleTriggerDto> listTriggers(Resource resource, Resource triggerResource) {
        List<RuleTrigger> ruleTriggers = triggerRepository.findAll();
        if (triggerResource != null)
            ruleTriggers = ruleTriggers.stream().filter(trigger -> trigger.getTriggerResource() == triggerResource).toList();
        if (resource != null)
            ruleTriggers = ruleTriggers.stream().filter(trigger -> (trigger.getResource() == resource)).toList();
        return ruleTriggers.stream().map(RuleTrigger::mapToDto).toList();
    }

    @Override
    public RuleTriggerDetailDto createTrigger(RuleTriggerRequestDto request) {

        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }

        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (request.getTriggerType() == null) {
            throw new ValidationException("Property trigger type cannot be empty.");
        }

        if (triggerRepository.findAllByTriggerResource(request.getTriggerResource()).stream().anyMatch(trigger -> Objects.equals(trigger.getName(), request.getName())))
            throw new ValidationException("Rule trigger with this name already exists for this trigger resource.");

        if (request.getActions().isEmpty() && request.getActionGroupsUuids().isEmpty())
            throw new ValidationException("Trigger must contain at least one action or action group.");


        RuleTrigger trigger = new RuleTrigger();

        trigger.setName(request.getName());
        trigger.setDescription(request.getDescription());
        trigger.setResource(request.getResource());
        trigger.setTriggerResource(request.getTriggerResource());
        trigger.setTriggerType(request.getTriggerType());
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

        List<RuleAction> actions = createActions(request.getActions(), trigger, null);

        // If there is IGNORE action in actions, do not create any action groups, since it is supposed to be the only action in the trigger
        if (actions.stream().anyMatch(action -> action.getActionType() == RuleActionType.IGNORE) && !request.getActionGroupsUuids().isEmpty())
            throw new ValidationException("Trigger has action of Ignore type, cannot create action groups for such trigger.");
        List<RuleActionGroup> actionGroups = new ArrayList<>();
        for (String actionGroupUuid : request.getActionGroupsUuids()) {
            Optional<RuleActionGroup> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
            if (actionGroup.isPresent() && actionGroup.get().getResource() == request.getResource()) {
                actionGroups.add(actionGroup.get());
            } else {
                throw new ValidationException("Action group with UUID " + actionGroupUuid + " is either not present or resource of the group does not match trigger resource.");
            }
        }

        trigger.setActionGroups(actionGroups);
        trigger.setRules(rules);
        trigger.setActions(actions);
        return trigger.mapToDetailDto();
    }

    @Override
    public RuleTriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException {
        return getRuleTriggerEntity(triggerUuid).mapToDetailDto();
    }

    @Override
    public RuleTrigger getRuleTriggerEntity(String triggerUuid) throws NotFoundException {
        return triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(RuleTrigger.class, triggerUuid));
    }


    @Override
    public RuleTriggerDetailDto updateTrigger(String triggerUuid, UpdateRuleTriggerRequestDto request) throws NotFoundException {


        if (request.getTriggerType() == null) {
            throw new ValidationException("Property trigger type cannot be empty.");
        }

        if (request.getActionGroupsUuids().isEmpty() && request.getActions().isEmpty()) {
            throw new ValidationException("Cannot update a trigger without any actions or action groups.");
        }


        RuleTrigger trigger = getRuleTriggerEntity(triggerUuid);

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

        List<RuleAction> actions = createActions(request.getActions(), trigger, null);
        // If there is IGNORE action in actions, do not create any action groups, since it is supposed to be the only action in the trigger
        if (actions.stream().anyMatch(action -> action.getActionType() == RuleActionType.IGNORE) && !request.getActionGroupsUuids().isEmpty())
            throw new ValidationException("Trigger has action of Ignore type, cannot create action groups for such trigger.");
        List<RuleActionGroup> actionGroups = new ArrayList<>();

        for (String actionGroupUuid : request.getActionGroupsUuids()) {
            Optional<RuleActionGroup> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
            if (actionGroup.isPresent() && actionGroup.get().getResource() == trigger.getResource()) {
                actionGroups.add(actionGroup.get());
            } else {
                throw new ValidationException("Action group with UUID " + actionGroupUuid + " is either not present or resource of the group does not match trigger resource.");
            }
        }

        trigger.setActionGroups(actionGroups);
        trigger.setRules(rules);
        trigger.setActions(actions);
        trigger.setDescription(request.getDescription());
        trigger.setTriggerResource(request.getTriggerResource());
        trigger.setTriggerType(request.getTriggerType());

        triggerRepository.save(trigger);
        return trigger.mapToDetailDto();
    }

    @Override
    public void deleteTrigger(String triggerUuid) throws NotFoundException {
        triggerRepository.delete(getRuleTriggerEntity(triggerUuid));
    }


    private List<RuleCondition> createConditions(List<RuleConditionRequestDto> conditionRequestDtos, Rule rule, RuleConditionGroup conditionGroup) {
        List<RuleCondition> conditions = new ArrayList<>();
        for (RuleConditionRequestDto conditionRequestDto : conditionRequestDtos) {
            if (conditionRequestDto.getFieldSource() == null || conditionRequestDto.getFieldIdentifier() == null || conditionRequestDto.getOperator() == null)
                throw new ValidationException("Missing field source, field identifier or operator in a condition.");
            RuleCondition condition = new RuleCondition();
            if (rule != null) {
                condition.setRule(rule);
            } else {
                condition.setRuleConditionGroup(conditionGroup);
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

    private List<RuleAction> createActions(List<RuleActionRequestDto> actionRequestDtos, RuleTrigger trigger, RuleActionGroup actionGroup) {
        List<RuleAction> actions = new ArrayList<>();
        for (RuleActionRequestDto actionRequestDto : actionRequestDtos) {
            if (actionRequestDto.getActionType() == null)
                throw new ValidationException("Missing action type in an action.");
            // If the Action Type is Ignore, it must be the only action in the list
            if (actionRequestDto.getActionType() == RuleActionType.IGNORE && actionRequestDtos.size() > 1)
                throw new ValidationException("Actions contain action with Action Type Ignore, it must be the only action in the list.");
            RuleAction action = new RuleAction();
            if (trigger != null) {
                action.setRuleTrigger(trigger);
            } else {
                action.setRuleActionGroup(actionGroup);
            }
            action.setActionType(actionRequestDto.getActionType());
            action.setFieldSource(actionRequestDto.getFieldSource());
            action.setFieldIdentifier(actionRequestDto.getFieldIdentifier());
            action.setActionData(actionRequestDto.getActionData());
            actionRepository.save(action);

            actions.add(action);
        }
        return actions;
    }


}

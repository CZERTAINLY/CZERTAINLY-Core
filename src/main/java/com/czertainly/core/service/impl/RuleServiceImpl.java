package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.rules.*;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.RuleService;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

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
    public List<RuleDto> listRules() {
        return ruleRepository.findAll().stream().map(Rule::mapToDto).toList();
    }

    @Override
    public RuleDetailDto createRule(RuleRequestDto request) {
        return createRuleEntity(request).mapToDetailDto();
    }

    private Rule createRuleEntity(RuleRequestDto request) {
        Rule rule = new Rule();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setResource(request.getResource());
        rule.setResourceType(request.getResourceType());
        rule.setResourceFormat(request.getResourceFormat());
        if (request.getConditions() != null) rule.setConditions(createConditions(request.getConditions(), rule, null));

        List<RuleConditionGroup> ruleConditionGroups = new ArrayList<>();

        if (request.getConditionGroups() != null) {
            for (RuleConditionGroupRequestDto ruleConditionGroupRequestDto : request.getConditionGroups()) {
                if (ruleConditionGroupRequestDto.getResource() == request.getResource()) {
                    ruleConditionGroups.add(createConditionGroupEntity(ruleConditionGroupRequestDto));
                }
            }
        }

        if (request.getConditionGroupsUuids() != null) {
            for (String conditionGroupUuid : request.getConditionGroupsUuids()) {
                Optional<RuleConditionGroup> ruleConditionGroup = conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid));
                if (ruleConditionGroup.isPresent() && ruleConditionGroup.get().getResource() == request.getResource()) ruleConditionGroups.add(ruleConditionGroup.get());
            }
        }

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
    public RuleDetailDto updateRule(String ruleUuid, RuleRequestDto request) throws NotFoundException {
        Rule rule = getRuleEntity(ruleUuid);
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setResource(request.getResource());
        rule.setResourceType(request.getResourceType());
        rule.setResourceFormat(request.getResourceFormat());
        conditionRepository.deleteAll(rule.getConditions());
        if (request.getConditions() != null) rule.setConditions(createConditions(request.getConditions(), rule, null));

        List<RuleConditionGroup> ruleConditionGroups = new ArrayList<>();

        if (request.getConditionGroups() != null) {
            for (RuleConditionGroupRequestDto ruleConditionGroupRequestDto : request.getConditionGroups()) {
                if (ruleConditionGroupRequestDto.getResource() == request.getResource()) {
                    ruleConditionGroups.add(createConditionGroupEntity(ruleConditionGroupRequestDto));
                }
            }
        }

        if (request.getConditionGroupsUuids() != null) {
            for (String conditionGroupUuid : request.getConditionGroupsUuids()) {
                Optional<RuleConditionGroup> ruleConditionGroup = conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid));
                if (ruleConditionGroup.isPresent() && ruleConditionGroup.get().getResource() == request.getResource()) ruleConditionGroups.add(ruleConditionGroup.get());
            }
        }

        rule.setConditionGroups(ruleConditionGroups);

        ruleRepository.save(rule);
        return rule.mapToDetailDto();
    }

    @Override
    public void deleteRule(String ruleUuid) throws NotFoundException {
        ruleRepository.delete(getRuleEntity(ruleUuid));
    }

    @Override
    public List<RuleConditionGroupDto> listConditionGroups() {
        return conditionGroupRepository.findAll().stream().map(RuleConditionGroup::mapToDto).toList();
    }

    @Override
    public RuleConditionGroupDetailDto createConditionGroup(RuleConditionGroupRequestDto request) {
        return createConditionGroupEntity(request).mapToDetailDto();
    }

    private RuleConditionGroup createConditionGroupEntity(RuleConditionGroupRequestDto request) {
        RuleConditionGroup conditionGroup = new RuleConditionGroup();
        conditionGroup.setName(request.getName());
        conditionGroup.setDescription(request.getDescription());
        conditionGroup.setResource(request.getResource());
        if (request.getConditions() != null)
            conditionGroup.setConditions(createConditions(request.getConditions(), null, conditionGroup));
        conditionGroupRepository.save(conditionGroup);
        return conditionGroup;
    }

    @Override
    public RuleConditionGroupDetailDto getConditionGroup(String conditionGroupUuid) throws NotFoundException {
        return getConditionGroupEntity(conditionGroupUuid).mapToDetailDto();
    }

    private RuleConditionGroup getConditionGroupEntity(String conditionGroupUuid) throws NotFoundException {
        return conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid)).orElseThrow(() -> new NotFoundException(RuleConditionGroup.class, conditionGroupUuid));
    }

    @Override
    public RuleConditionGroupDetailDto updateConditionGroup(String conditionGroupUuid, RuleConditionGroupRequestDto request) throws NotFoundException {
        RuleConditionGroup conditionGroup = getConditionGroupEntity(conditionGroupUuid);
        conditionGroup.setName(request.getName());
        conditionGroup.setDescription(request.getDescription());
        conditionGroup.setResource(request.getResource());
        conditionRepository.deleteAll(conditionGroup.getConditions());
        if (request.getConditions() != null)
            conditionGroup.setConditions(createConditions(request.getConditions(), null, conditionGroup));
        conditionGroupRepository.save(conditionGroup);
        return conditionGroup.mapToDetailDto();
    }

    @Override
    public void deleteConditionGroup(String conditionGroupUuid) throws NotFoundException {
        conditionGroupRepository.delete(getConditionGroupEntity(conditionGroupUuid));
    }

    @Override
    public List<RuleActionGroupDto> listActionGroups() {
        return actionGroupRepository.findAll().stream().map(RuleActionGroup::mapToDto).toList();
    }

    @Override
    public RuleActionGroupDetailDto createActionGroup(RuleActionGroupRequestDto request) {
        return createActionGroupEntity(request).mapToDetailDto();
    }

    private RuleActionGroup createActionGroupEntity(RuleActionGroupRequestDto request) {
        RuleActionGroup actionGroup = new RuleActionGroup();
        actionGroup.setName(request.getName());
        actionGroup.setDescription(request.getDescription());
        actionGroup.setResource(request.getResource());
        if (request.getActions() != null) actionGroup.setActions(createActions(request.getActions(), null, actionGroup));
        actionGroupRepository.save(actionGroup);
        return actionGroup;
    }

    @Override
    public RuleActionGroupDetailDto getActionGroup(String actionGroupUuid) throws NotFoundException {
        return getActionGroupEntity(actionGroupUuid).mapToDetailDto();
    }

    private RuleActionGroup getActionGroupEntity(String actionGroupUuid) throws NotFoundException {
        return actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid)).orElseThrow(() -> new NotFoundException(RuleActionGroup.class, actionGroupUuid));
    }

    @Override
    public RuleActionGroupDetailDto updateActionGroup(String actionGroupUuid, RuleActionGroupRequestDto request) throws NotFoundException {
        RuleActionGroup actionGroup = getActionGroupEntity(actionGroupUuid);
        actionGroup.setName(request.getName());
        actionGroup.setDescription(request.getDescription());
        actionGroup.setResource(request.getResource());
        actionRepository.deleteAll(actionGroup.getActions());
        if (request.getActions() != null) actionGroup.setActions(createActions(request.getActions(), null, actionGroup));
        actionGroupRepository.save(actionGroup);
        return actionGroup.mapToDetailDto();
    }

    @Override
    public void deleteActionGroup(String actionGroupUuid) throws NotFoundException {
        actionGroupRepository.delete(getActionGroupEntity(actionGroupUuid));
    }

    @Override
    public List<RuleTriggerDto> listTriggers() {
        return triggerRepository.findAll().stream().map(RuleTrigger::mapToDto).toList();
    }

    @Override
    public RuleTriggerDetailDto createTrigger(RuleTriggerRequestDto request) {
        RuleTrigger trigger = new RuleTrigger();
        trigger.setName(request.getName());
        trigger.setDescription(request.getDescription());
        trigger.setResource(request.getResource());
        trigger.setTriggerResource(request.getTriggerResource());
        trigger.setTriggerResourceUuid(UUID.fromString(request.getTriggerResourceUuid()));
        trigger.setTriggerType(request.getTriggerType());
        if (request.getActions() != null) trigger.setActions(createActions(request.getActions(), trigger, null));

        List<RuleActionGroup> actionGroups = new ArrayList<>();
        if (request.getActionGroups() != null) {
            for (RuleActionGroupRequestDto actionGroupRequestDto : request.getActionGroups()) {
                if (actionGroupRequestDto.getResource() == trigger.getResource()) actionGroups.add(createActionGroupEntity(actionGroupRequestDto));
            }
        }

        if (request.getActionGroupsUuids() != null) {
            for (String actionGroupUuid : request.getActionGroupsUuids()) {
                Optional<RuleActionGroup> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
                if (actionGroup.isPresent() && actionGroup.get().getResource() == trigger.getResource()) actionGroups.add(actionGroup.get());
            }
        }

        trigger.setActionGroups(actionGroups);

        List<Rule> rules = new ArrayList<>();
        if (request.getRules() != null) {
            for (RuleRequestDto ruleRequestDto : request.getRules()) {
                if (ruleRequestDto.getResource() == trigger.getResource()) rules.add(createRuleEntity(ruleRequestDto));
            }
        }

        if (request.getRulesUuids() != null) {
            for (String ruleUuid : request.getRulesUuids()) {
                Optional<Rule> rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid));
                if (rule.isPresent() && rule.get().getResource() == trigger.getResource()) rules.add(rule.get());
            }
        }

        trigger.setRules(rules);

        triggerRepository.save(trigger);
        return trigger.mapToDetailDto();
    }

    @Override
    public RuleTriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException {
        return getRuleTriggerEntity(triggerUuid).mapToDetailDto();
    }

    private RuleTrigger getRuleTriggerEntity(String triggerUuid) throws NotFoundException {
        return triggerRepository.findByUuid(SecuredUUID.fromString(triggerUuid)).orElseThrow(() -> new NotFoundException(RuleTrigger.class, triggerUuid));
    }


    @Override
    public RuleTriggerDetailDto updateTrigger(String triggerUuid, RuleTriggerRequestDto request) throws NotFoundException {
        RuleTrigger trigger = getRuleTriggerEntity(triggerUuid);
        trigger.setName(request.getName());
        trigger.setDescription(request.getDescription());
        trigger.setResource(request.getResource());
        trigger.setTriggerResource(request.getTriggerResource());
        trigger.setTriggerResourceUuid(UUID.fromString(request.getTriggerResourceUuid()));
        trigger.setTriggerType(request.getTriggerType());
        actionRepository.deleteAll(trigger.getActions());

        if (request.getActions() != null) trigger.setActions(createActions(request.getActions(), trigger, null));

        List<RuleActionGroup> actionGroups = new ArrayList<>();
        if (request.getActionGroups() != null) {
            for (RuleActionGroupRequestDto actionGroupRequestDto : request.getActionGroups()) {
                if (actionGroupRequestDto.getResource() == trigger.getResource()) actionGroups.add(createActionGroupEntity(actionGroupRequestDto));
            }
        }

        if (request.getActionGroupsUuids() != null) {
            for (String actionGroupUuid : request.getActionGroupsUuids()) {
                Optional<RuleActionGroup> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
                if (actionGroup.isPresent() && actionGroup.get().getResource() == trigger.getResource()) actionGroups.add(actionGroup.get());
            }
        }

        trigger.setActionGroups(actionGroups);

        List<Rule> rules = new ArrayList<>();
        if (request.getRules() != null) {
            for (RuleRequestDto ruleRequestDto : request.getRules()) {
                if (ruleRequestDto.getResource() == trigger.getResource()) rules.add(createRuleEntity(ruleRequestDto));
            }
        }

        if (request.getRulesUuids() != null) {
            for (String ruleUuid : request.getRulesUuids()) {
                Optional<Rule> rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid));
                if (rule.isPresent() && rule.get().getResource() == trigger.getResource()) rules.add(rule.get());
            }
        }

        trigger.setRules(rules);

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

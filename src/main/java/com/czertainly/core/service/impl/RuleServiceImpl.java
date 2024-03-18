package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.*;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.FilterFieldType;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.RuleService;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.BiFunction;

import static com.czertainly.api.model.core.search.FilterConditionOperator.*;
import static com.czertainly.api.model.core.search.FilterConditionOperator.NOT_EMPTY;

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

        if (ruleRepository.findAllByResource(request.getResource()).stream().map(Rule::getName).toList().contains(request.getName()))
            throw new ValidationException("Rule with this name already exists for this resource.");

        if (request.getConditionGroups() == null & request.getConditionGroupsUuids() == null & request.getConditions() == null) {
            throw new ValidationException("Cannot crate a rule without any conditions or any condition groups.");
        }

        Rule rule = new Rule();

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

        if (request.getConditions() != null)
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
    public RuleDetailDto updateRule(String ruleUuid, RuleRequestDto request) throws NotFoundException {

        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (request.getConditionGroups() == null & request.getConditionGroupsUuids() == null & request.getConditions() == null) {
            throw new ValidationException("Cannot update a rule without any conditions or any condition groups.");
        }


        Rule rule = getRuleEntity(ruleUuid);

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

        conditionRepository.deleteAll(rule.getConditions());
        if (request.getConditions() != null) {
            rule.setConditions(createConditions(request.getConditions(), rule, null));
        }


        rule.setDescription(request.getDescription());
        rule.setResource(request.getResource());
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
        if (resource == null) return conditionGroupRepository.findAll().stream().map(RuleConditionGroup::mapToDto).toList();
        return conditionGroupRepository.findAllByResource(resource).stream().map(RuleConditionGroup::mapToDto).toList();
    }

    @Override
    public RuleConditionGroupDetailDto createConditionGroup(RuleConditionGroupRequestDto request) {
        return createConditionGroupEntity(request).mapToDetailDto();
    }

    private RuleConditionGroup createConditionGroupEntity(RuleConditionGroupRequestDto request) {
        if (request.getConditions() == null) {
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
    public RuleConditionGroupDetailDto getConditionGroup(String conditionGroupUuid) throws NotFoundException {
        return getConditionGroupEntity(conditionGroupUuid).mapToDetailDto();
    }

    private RuleConditionGroup getConditionGroupEntity(String conditionGroupUuid) throws NotFoundException {
        return conditionGroupRepository.findByUuid(SecuredUUID.fromString(conditionGroupUuid)).orElseThrow(() -> new NotFoundException(RuleConditionGroup.class, conditionGroupUuid));
    }

    @Override
    public RuleConditionGroupDetailDto updateConditionGroup(String conditionGroupUuid, RuleConditionGroupRequestDto request) throws NotFoundException {
        if (request.getConditions() == null) {
            throw new ValidationException("Cannot update a condition group without any conditions.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }
        RuleConditionGroup conditionGroup = getConditionGroupEntity(conditionGroupUuid);
        conditionGroup.setDescription(request.getDescription());
        conditionGroup.setResource(request.getResource());
        conditionRepository.deleteAll(conditionGroup.getConditions());
        conditionGroup.setConditions(createConditions(request.getConditions(), null, conditionGroup));
        conditionGroupRepository.save(conditionGroup);
        return conditionGroup.mapToDetailDto();
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
    public RuleActionGroupDetailDto createActionGroup(RuleActionGroupRequestDto request) {
        return createActionGroupEntity(request).mapToDetailDto();
    }

    private RuleActionGroup createActionGroupEntity(RuleActionGroupRequestDto request) {
        if (request.getActions() == null) {
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
    public RuleActionGroupDetailDto getActionGroup(String actionGroupUuid) throws NotFoundException {
        return getActionGroupEntity(actionGroupUuid).mapToDetailDto();
    }

    private RuleActionGroup getActionGroupEntity(String actionGroupUuid) throws NotFoundException {
        return actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid)).orElseThrow(() -> new NotFoundException(RuleActionGroup.class, actionGroupUuid));
    }

    @Override
    public RuleActionGroupDetailDto updateActionGroup(String actionGroupUuid, RuleActionGroupRequestDto request) throws NotFoundException {
        if (request.getActions() == null) {
            throw new ValidationException("Cannot update an action group without any actions.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }
        RuleActionGroup actionGroup = getActionGroupEntity(actionGroupUuid);
        actionGroup.setDescription(request.getDescription());
        actionGroup.setResource(request.getResource());
        actionRepository.deleteAll(actionGroup.getActions());
        actionGroup.setActions(createActions(request.getActions(), null, actionGroup));
        actionGroupRepository.save(actionGroup);
        return actionGroup.mapToDetailDto();
    }

    @Override
    public void deleteActionGroup(String actionGroupUuid) throws NotFoundException {
        actionGroupRepository.delete(getActionGroupEntity(actionGroupUuid));
    }

    @Override
    public List<RuleTriggerDto> listTriggers(Resource resource, Resource triggerResource) {
        List<RuleTrigger> ruleTriggers = triggerRepository.findAll();
        if (triggerResource != null) ruleTriggers = ruleTriggers.stream().filter(trigger -> trigger.getTriggerResource() == triggerResource).toList();
        if (resource != null) ruleTriggers = ruleTriggers.stream().filter(trigger -> (trigger.getResource() == resource)).toList();
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

        if (triggerRepository.findAllByTriggerResource(request.getTriggerResource()).stream().map(RuleTrigger::getName).toList().contains(request.getName()))
            throw new ValidationException("Rule trigger with this name already exists for this trigger resource.");

        if (request.getActionGroups() == null & request.getActionGroupsUuids() == null & request.getRules() == null & request.getRulesUuids() == null & request.getActions() == null) {
            throw new ValidationException("Cannot create a trigger without any actions, action groups or rules.");
        }

        RuleTrigger trigger = new RuleTrigger();

        List<RuleActionGroup> actionGroups = new ArrayList<>();
        if (request.getActionGroups() != null) {
            for (RuleActionGroupRequestDto actionGroupRequestDto : request.getActionGroups()) {
                if (actionGroupRequestDto.getResource() == request.getResource()) actionGroups.add(createActionGroupEntity(actionGroupRequestDto));
            }
        }

        if (request.getActionGroupsUuids() != null) {
            for (String actionGroupUuid : request.getActionGroupsUuids()) {
                Optional<RuleActionGroup> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
                if (actionGroup.isPresent() && actionGroup.get().getResource() == request.getResource()) actionGroups.add(actionGroup.get());
            }
        }

        List<Rule> rules = new ArrayList<>();
        if (request.getRules() != null) {
            for (RuleRequestDto ruleRequestDto : request.getRules()) {
                if (ruleRequestDto.getResource() == request.getResource()) rules.add(createRuleEntity(ruleRequestDto));
            }
        }

        if (request.getRulesUuids() != null) {
            for (String ruleUuid : request.getRulesUuids()) {
                Optional<Rule> rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid));
                if (rule.isPresent() && rule.get().getResource() == request.getResource()) rules.add(rule.get());
            }
        }

        if (request.getActions() != null) trigger.setActions(createActions(request.getActions(), trigger, null));


        trigger.setName(request.getName());
        trigger.setDescription(request.getDescription());
        trigger.setResource(request.getResource());
        trigger.setTriggerResource(request.getTriggerResource());
        if (request.getTriggerResourceUuid()!= null) trigger.setTriggerResourceUuid(UUID.fromString(request.getTriggerResourceUuid()));
        trigger.setTriggerType(request.getTriggerType());
        trigger.setRules(rules);
        trigger.setActionGroups(actionGroups);
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

        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (request.getTriggerType() == null) {
            throw new ValidationException("Property trigger type cannot be empty.");
        }

        if (request.getActionGroups() == null & request.getActionGroupsUuids() == null & request.getRules() == null & request.getRulesUuids() == null & request.getActions() == null) {
            throw new ValidationException("Cannot update a trigger without any actions, action groups or rules.");
        }

        RuleTrigger trigger = getRuleTriggerEntity(triggerUuid);

        List<RuleActionGroup> actionGroups = new ArrayList<>();
        if (request.getActionGroups() != null) {
            for (RuleActionGroupRequestDto actionGroupRequestDto : request.getActionGroups()) {
                if (actionGroupRequestDto.getResource() == request.getResource()) actionGroups.add(createActionGroupEntity(actionGroupRequestDto));
            }
        }

        if (request.getActionGroupsUuids() != null) {
            for (String actionGroupUuid : request.getActionGroupsUuids()) {
                Optional<RuleActionGroup> actionGroup = actionGroupRepository.findByUuid(SecuredUUID.fromString(actionGroupUuid));
                if (actionGroup.isPresent() && actionGroup.get().getResource() == request.getResource()) actionGroups.add(actionGroup.get());
            }
        }


        List<Rule> rules = new ArrayList<>();
        if (request.getRules() != null) {
            for (RuleRequestDto ruleRequestDto : request.getRules()) {
                if (ruleRequestDto.getResource() == request.getResource()) rules.add(createRuleEntity(ruleRequestDto));
            }
        }

        if (request.getRulesUuids() != null) {
            for (String ruleUuid : request.getRulesUuids()) {
                Optional<Rule> rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid));
                if (rule.isPresent() && rule.get().getResource() == request.getResource()) rules.add(rule.get());
            }
        }

        actionRepository.deleteAll(trigger.getActions());
        if (request.getActions() != null) {
            trigger.setActions(createActions(request.getActions(), trigger, null));
        }


        trigger.setRules(rules);

        trigger.setDescription(request.getDescription());
        trigger.setResource(request.getResource());
        trigger.setTriggerResource(request.getTriggerResource());
        if (request.getTriggerResourceUuid() != null) trigger.setTriggerResourceUuid(UUID.fromString(request.getTriggerResourceUuid()));
        trigger.setTriggerType(request.getTriggerType());
        trigger.setActionGroups(actionGroups);

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
            if (conditionRequestDto.getFieldSource() == null || conditionRequestDto.getFieldIdentifier() == null || conditionRequestDto.getOperator() == null) continue;
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
            if (actionRequestDto.getActionType() == null) continue;
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

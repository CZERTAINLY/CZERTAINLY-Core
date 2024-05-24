package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.Condition;
import com.czertainly.core.dao.entity.workflows.ConditionItem;
import com.czertainly.core.dao.entity.workflows.Rule;
import com.czertainly.core.dao.repository.workflows.ConditionItemRepository;
import com.czertainly.core.dao.repository.workflows.ConditionRepository;
import com.czertainly.core.dao.repository.workflows.RuleRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class RuleServiceImpl implements RuleService {

    private ConditionRepository conditionRepository;
    private ConditionItemRepository conditionItemRepository;
    private RuleRepository ruleRepository;

    @Autowired
    public void setConditionRepository(ConditionRepository conditionRepository) {
        this.conditionRepository = conditionRepository;
    }

    @Autowired
    public void setConditionItemRepository(ConditionItemRepository conditionItemRepository) {
        this.conditionItemRepository = conditionItemRepository;
    }

    @Autowired
    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    //region Conditions

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.LIST)
    public List<ConditionDto> listConditions(Resource resource) {
        if (resource == null) return conditionRepository.findAll().stream().map(Condition::mapToDto).toList();
        return conditionRepository.findAllByResource(resource).stream().map(Condition::mapToDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.DETAIL)
    public ConditionDto getCondition(String conditionUuid) throws NotFoundException {
        return conditionRepository.findByUuid(SecuredUUID.fromString(conditionUuid)).orElseThrow(() -> new NotFoundException(Condition.class, conditionUuid)).mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.CREATE)
    public ConditionDto createCondition(ConditionRequestDto request) throws AlreadyExistException {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot create a condition without any condition items.");
        }
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (conditionRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("Condition with same name already exists.");
        }

        Condition condition = new Condition();
        condition.setName(request.getName());
        condition.setDescription(request.getDescription());
        condition.setType(request.getType());
        condition.setResource(request.getResource());
        conditionRepository.save(condition);
        condition.setItems(createConditionItems(request.getItems(), condition));

        return condition.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.UPDATE)
    public ConditionDto updateCondition(String conditionUuid, UpdateConditionRequestDto request) throws NotFoundException {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot update a condition without any condition items.");
        }

        Condition condition = conditionRepository.findByUuid(SecuredUUID.fromString(conditionUuid)).orElseThrow(() -> new NotFoundException(Condition.class, conditionUuid));
        conditionItemRepository.deleteAll(condition.getItems());

        condition.setDescription(request.getDescription());
        condition.setItems(createConditionItems(request.getItems(), condition));
        conditionRepository.save(condition);

        return condition.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.DELETE)
    public void deleteCondition(String conditionUuid) throws NotFoundException {
        Condition condition = conditionRepository.findByUuid(SecuredUUID.fromString(conditionUuid)).orElseThrow(() -> new NotFoundException(Condition.class, conditionUuid));
        conditionRepository.delete(condition);
    }

    private List<ConditionItem> createConditionItems(List<ConditionItemRequestDto> conditionItemRequestDtos, Condition condition) {
        List<ConditionItem> conditionItems = new ArrayList<>();
        for (ConditionItemRequestDto conditionItemRequestDto : conditionItemRequestDtos) {
            if (conditionItemRequestDto.getFieldSource() == null
                    || conditionItemRequestDto.getFieldIdentifier() == null
                    || conditionItemRequestDto.getOperator() == null) {
                throw new ValidationException("Missing field source, field identifier or operator in a condition.");
            }

            ConditionItem conditionItem = new ConditionItem();
            conditionItem.setCondition(condition);
            conditionItem.setFieldSource(conditionItemRequestDto.getFieldSource());
            conditionItem.setFieldIdentifier(conditionItemRequestDto.getFieldIdentifier());
            conditionItem.setOperator(conditionItemRequestDto.getOperator());
            conditionItem.setValue(conditionItemRequestDto.getValue());
            conditionItemRepository.save(conditionItem);

            conditionItems.add(conditionItem);
        }
        return conditionItems;
    }

    //endregion

    //region Rules

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.LIST)
    public List<RuleDto> listRules(Resource resource) {
        if (resource == null) return ruleRepository.findAll().stream().map(Rule::mapToDto).toList();
        return ruleRepository.findAllByResource(resource).stream().map(Rule::mapToDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.DETAIL)
    public RuleDetailDto getRule(String ruleUuid) throws NotFoundException {
        return ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid)).orElseThrow(() -> new NotFoundException(Rule.class, ruleUuid)).mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.CREATE)
    public RuleDetailDto createRule(RuleRequestDto request) throws AlreadyExistException, NotFoundException {
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (ruleRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("Rule with same name already exists.");
        }

        if (request.getConditionsUuids().isEmpty()) {
            throw new ValidationException("Rule has to contain at least one condition.");
        }

        Rule rule = new Rule();
        List<Condition> conditions = new ArrayList<>();

        for (String conditionUuid : request.getConditionsUuids()) {
            Condition condition = conditionRepository.findByUuid(SecuredUUID.fromString(conditionUuid)).orElseThrow(() -> new NotFoundException(Condition.class, conditionUuid));
            if (condition.getResource() != request.getResource()) {
                throw new ValidationException("Resource of condition with UUID " + conditionUuid + " does not match rule resource.");
            }
            conditions.add(condition);
        }

        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setResource(request.getResource());
        rule.setConditions(conditions);

        ruleRepository.save(rule);
        return rule.mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.UPDATE)
    public RuleDetailDto updateRule(String ruleUuid, UpdateRuleRequestDto request) throws NotFoundException {
        if (request.getConditionsUuids().isEmpty()) {
            throw new ValidationException("Rule has to contain at least one condition.");
        }

        List<Condition> conditions = new ArrayList<>();
        Rule rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid)).orElseThrow(() -> new NotFoundException(Rule.class, ruleUuid));

        for (String conditionUuid : request.getConditionsUuids()) {
            Condition condition = conditionRepository.findByUuid(SecuredUUID.fromString(conditionUuid)).orElseThrow(() -> new NotFoundException(Condition.class, conditionUuid));
            if (condition.getResource() != rule.getResource()) {
                throw new ValidationException("Resource of condition with UUID " + conditionUuid + " does not match rule resource.");
            }
            conditions.add(condition);
        }

        rule.setDescription(request.getDescription());
        rule.setConditions(conditions);

        ruleRepository.save(rule);
        return rule.mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.RULE, action = ResourceAction.DELETE)
    public void deleteRule(String ruleUuid) throws NotFoundException {
        Rule rule = ruleRepository.findByUuid(SecuredUUID.fromString(ruleUuid)).orElseThrow(() -> new NotFoundException(Rule.class, ruleUuid));
        ruleRepository.delete(rule);
    }

    //endregion
}

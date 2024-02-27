package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.RuleController;
import com.czertainly.api.model.core.rules.*;
import com.czertainly.core.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RuleControllerImpl implements RuleController {

    private RuleService ruleService;
    @Autowired
    public void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }


    @Override
    public List<RuleDto> listRules() {
        return ruleService.listRules();
    }

    @Override
    public RuleDetailDto createRule(RuleRequestDto request) {
        return ruleService.createRule(request);
    }

    @Override
    public RuleDetailDto getRule(String ruleUuid) throws NotFoundException {
        return ruleService.getRule(ruleUuid);
    }

    @Override
    public RuleDetailDto updateRule(String ruleUuid, RuleRequestDto request) throws NotFoundException {
        return ruleService.updateRule(ruleUuid, request);
    }

    @Override
    public void deleteRule(String ruleUuid) throws NotFoundException {
        ruleService.deleteRule(ruleUuid);
    }

    @Override
    public List<RuleConditionGroupDto> listConditionGroups() {
        return ruleService.listConditionGroups();
    }

    @Override
    public RuleConditionGroupDetailDto createConditionGroup(RuleConditionGroupRequestDto request) {
        return ruleService.createConditionGroup(request);
    }

    @Override
    public RuleConditionGroupDetailDto getConditionGroup(String conditionGroupUuid) throws NotFoundException {
        return ruleService.getConditionGroup(conditionGroupUuid);
    }

    @Override
    public RuleConditionGroupDetailDto updateConditionGroup(String conditionGroupUuid, RuleConditionGroupRequestDto request) throws NotFoundException {
        return ruleService.updateConditionGroup(conditionGroupUuid, request);
    }

    @Override
    public void deleteConditionGroup(String conditionGroupUuid) throws NotFoundException {
        ruleService.deleteConditionGroup(conditionGroupUuid);
    }

    @Override
    public List<RuleActionGroupDto> listActionGroups() {
        return ruleService.listActionGroups();
    }

    @Override
    public RuleActionGroupDetailDto createActionGroup(RuleActionGroupRequestDto request) {
        return ruleService.createActionGroup(request);
    }

    @Override
    public RuleActionGroupDetailDto getActionGroup(String actionGroupUuid) throws NotFoundException {
        return ruleService.getActionGroup(actionGroupUuid);
    }

    @Override
    public RuleActionGroupDetailDto updateActionGroup(String actionGroupUuid, RuleActionGroupRequestDto request) throws NotFoundException {
        return ruleService.updateActionGroup(actionGroupUuid, request);
    }

    @Override
    public void deleteActionGroup(String actionGroupUuid) throws NotFoundException {
        ruleService.deleteActionGroup(actionGroupUuid);
    }

    @Override
    public List<RuleTriggerDto> listTriggers() {
        return ruleService.listTriggers();
    }

    @Override
    public RuleTriggerDetailDto createTrigger(RuleTriggerRequestDto request) {
        return ruleService.createTrigger(request);
    }

    @Override
    public RuleTriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException {
        return ruleService.getTrigger(triggerUuid);
    }

    @Override
    public RuleTriggerDetailDto updateTrigger(String triggerUuid, RuleTriggerRequestDto request) throws NotFoundException {
        return ruleService.updateTrigger(triggerUuid, request);
    }

    @Override
    public void deleteTrigger(String triggerUuid) throws NotFoundException {
        ruleService.deleteTrigger(triggerUuid);
    }
}

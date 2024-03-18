package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.RuleController;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.*;
import com.czertainly.core.service.RuleService;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RuleControllerImpl implements RuleController {

    private RuleService ruleService;
    @Autowired
    public void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
    }


    @Override
    public List<RuleDto> listRules(Resource resource) {
        return ruleService.listRules(resource);
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
    public RuleDetailDto updateRule(String ruleUuid, UpdateRuleRequestDto request) throws NotFoundException {
        return ruleService.updateRule(ruleUuid, request);
    }

    @Override
    public void deleteRule(String ruleUuid) throws NotFoundException {
        ruleService.deleteRule(ruleUuid);
    }

    @Override
    public List<RuleConditionGroupDto> listConditionGroups(Resource resource) {
        return ruleService.listConditionGroups(resource);
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
    public RuleConditionGroupDetailDto updateConditionGroup(String conditionGroupUuid, UpdateConditionGroupRequestDto request) throws NotFoundException {
        return ruleService.updateConditionGroup(conditionGroupUuid, request);
    }

    @Override
    public void deleteConditionGroup(String conditionGroupUuid) throws NotFoundException {
        ruleService.deleteConditionGroup(conditionGroupUuid);
    }

    @Override
    public List<RuleActionGroupDto> listActionGroups(Resource resource) {
        return ruleService.listActionGroups(resource);
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
    public RuleActionGroupDetailDto updateActionGroup(String actionGroupUuid, UpdateActionGroupRequestDto request) throws NotFoundException {
        return ruleService.updateActionGroup(actionGroupUuid, request);
    }

    @Override
    public void deleteActionGroup(String actionGroupUuid) throws NotFoundException {
        ruleService.deleteActionGroup(actionGroupUuid);
    }

    @Override
    public List<RuleTriggerDto> listTriggers(Resource resource, Resource triggerResource) {
        return ruleService.listTriggers(resource, triggerResource);
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
    public RuleTriggerDetailDto updateTrigger(String triggerUuid, UpdateTriggerRequestDto request) throws NotFoundException {
        return ruleService.updateTrigger(triggerUuid, request);
    }

    @Override
    public void deleteTrigger(String triggerUuid) throws NotFoundException {
        ruleService.deleteTrigger(triggerUuid);
    }
}

package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.*;

import java.util.List;

public interface RuleService {

    List<RuleDto> listRules(Resource resource);
    RuleDetailDto createRule(RuleRequestDto request);
    RuleDetailDto getRule(String ruleUuid) throws NotFoundException;
    RuleDetailDto updateRule(String ruleUuid, UpdateRuleRequestDto request) throws NotFoundException;
    void deleteRule(String ruleUuid) throws NotFoundException;

    List<RuleConditionGroupDto> listConditionGroups(Resource resource);
    RuleConditionGroupDetailDto createConditionGroup(RuleConditionGroupRequestDto request);
    RuleConditionGroupDetailDto getConditionGroup(String conditionGroupUuid) throws NotFoundException;
    RuleConditionGroupDetailDto updateConditionGroup(String conditionGroupUuid, UpdateConditionGroupRequestDto request) throws NotFoundException;
    void deleteConditionGroup(String conditionGroupUuid) throws NotFoundException;

    List<RuleActionGroupDto> listActionGroups(Resource resource);
    RuleActionGroupDetailDto createActionGroup(RuleActionGroupRequestDto request);
    RuleActionGroupDetailDto getActionGroup(String actionGroupUuid) throws NotFoundException;
    RuleActionGroupDetailDto updateActionGroup(String actionGroupUuid, UpdateActionGroupRequestDto request) throws NotFoundException;
    void deleteActionGroup(String actionGroupUuid) throws NotFoundException;

    List<RuleTriggerDto> listTriggers(Resource resource, Resource triggerResource);
    RuleTriggerDetailDto createTrigger(RuleTriggerRequestDto request);
    RuleTriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException;
    RuleTriggerDetailDto updateTrigger(String triggerUuid, UpdateTriggerRequestDto request) throws NotFoundException;
    void deleteTrigger(String triggerUuid) throws NotFoundException;

}

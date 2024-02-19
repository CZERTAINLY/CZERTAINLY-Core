package com.czertainly.core.service;

import com.czertainly.api.model.core.rules.*;

import java.util.List;


public interface RuleService {

    List<RuleDto> listRules();
    RuleDto createRule(RuleRequestDto request);
    RuleDto updateRule(String ruleUuid, RuleRequestDto request);
    void deleteRule(String ruleUuid);

    List<RuleConditionGroupDto> listConditionGroups();
    RuleConditionGroupDto createConditionGroup(RuleConditionGroupRequestDto request);
    RuleConditionGroupDto updateConditionGroup(String conditionGroupUuid, RuleConditionGroupRequestDto request);
    void deleteConditionGroup(String conditionGroupUuid);

    List<RuleActionGroupDto> listActionGroups();
    RuleActionGroupDto createActionGroup(RuleActionGroupRequestDto request);
    RuleActionGroupDto updateActionGroup(String actionGroupUuid, RuleActionGroupRequestDto request);
    void deleteActionGroup(String actionGroupUuid);

    List<RuleTriggerDto> listTriggers();
    RuleTriggerDto createTrigger(RuleTriggerRequestDto request);
    RuleTriggerDto updateTrigger(String triggerUuid, RuleTriggerRequestDto request);
    void deleteTrigger(String triggerUuid);

}

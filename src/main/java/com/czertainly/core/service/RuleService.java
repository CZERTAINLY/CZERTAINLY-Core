package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.*;
import com.czertainly.core.dao.entity.RuleTrigger;
import com.czertainly.core.dao.entity.RuleTriggerHistory;
import com.czertainly.core.dao.entity.RuleTriggerHistoryRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RuleService {

    List<RuleDto> listRules(Resource resource);
    RuleDetailDto createRule(RuleRequestDto request);
    RuleDetailDto getRule(String ruleUuid) throws NotFoundException;
    RuleDetailDto updateRule(String ruleUuid, UpdateRuleRequestDto request) throws NotFoundException;
    void deleteRule(String ruleUuid) throws NotFoundException;

    List<RuleConditionGroupDto> listConditionGroups(Resource resource);
    RuleConditionGroupDto createConditionGroup(RuleConditionGroupRequestDto request);
    RuleConditionGroupDto getConditionGroup(String conditionGroupUuid) throws NotFoundException;
    RuleConditionGroupDto updateConditionGroup(String conditionGroupUuid, UpdateRuleConditionGroupRequestDto request) throws NotFoundException;
    void deleteConditionGroup(String conditionGroupUuid) throws NotFoundException;

    List<RuleActionGroupDto> listActionGroups(Resource resource);
    RuleActionGroupDto createActionGroup(RuleActionGroupRequestDto request);
    RuleActionGroupDto getActionGroup(String actionGroupUuid) throws NotFoundException;
    RuleActionGroupDto updateActionGroup(String actionGroupUuid, UpdateRuleActionGroupRequestDto request) throws NotFoundException;
    void deleteActionGroup(String actionGroupUuid) throws NotFoundException;

    List<RuleTriggerDto> listTriggers(Resource resource, Resource triggerResource);
    RuleTriggerDetailDto createTrigger(RuleTriggerRequestDto request);
    RuleTriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException;
    RuleTrigger getRuleTriggerEntity(String triggerUuid) throws NotFoundException;
    RuleTriggerDetailDto updateTrigger(String triggerUuid, UpdateRuleTriggerRequestDto request) throws NotFoundException;
    void deleteTrigger(String triggerUuid) throws NotFoundException;

    List<RuleTriggerHistoryDto> getTriggerHistory(String triggerUuid, String triggerObjectUuid);
    RuleTriggerHistory createTriggerHistory(LocalDateTime triggeredAt, UUID triggerUuid, UUID triggerAssociationUuid, UUID objectUuid, UUID referenceObjectUuid);
    RuleTriggerHistoryRecord createRuleTriggerHistoryRecord(RuleTriggerHistory triggerHistory, UUID actionUuid, UUID conditionUuid, String message);
}

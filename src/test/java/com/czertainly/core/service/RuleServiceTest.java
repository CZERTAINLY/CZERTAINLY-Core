package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.*;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class RuleServiceTest extends BaseSpringBootTest {

    @Autowired
    RuleService ruleService;
    RuleConditionRequestDto conditionRequestDto;
    RuleActionRequestDto actionRequestDto;


    @BeforeEach
    public void setUp() {

        conditionRequestDto = new RuleConditionRequestDto();
        conditionRequestDto.setFieldSource(FilterFieldSource.PROPERTY);
        conditionRequestDto.setFieldIdentifier("identifier");
        conditionRequestDto.setOperator(FilterConditionOperator.EQUALS);
        conditionRequestDto.setValue(123);

        actionRequestDto = new RuleActionRequestDto();
        actionRequestDto.setActionType(RuleActionType.SET_FIELD);


    }


    @Test
    public void testRule() throws NotFoundException {
        RuleRequestDto ruleRequestDto = new RuleRequestDto();
        ruleRequestDto.setName("name");
        ruleRequestDto.setResource(Resource.CERTIFICATE);
        Assertions.assertThrows(ValidationException.class, () -> ruleService.createRule(ruleRequestDto));

        ruleRequestDto.setConditions(List.of(conditionRequestDto));
        RuleDetailDto ruleDetailDto = ruleService.createRule(ruleRequestDto);
        Assertions.assertNotNull(ruleDetailDto);


        UpdateRuleRequestDto updateRuleRequestDto = new UpdateRuleRequestDto();
        updateRuleRequestDto.setDescription("description");
        updateRuleRequestDto.setConditions(ruleRequestDto.getConditions());
        Assertions.assertEquals("description", ruleService.updateRule(ruleDetailDto.getUuid(), updateRuleRequestDto).getDescription());

        Assertions.assertNotNull(ruleService.getRule(ruleDetailDto.getUuid()));

        Assertions.assertNotEquals(0, ruleService.listRules(null).size());

        ruleService.deleteRule(ruleDetailDto.getUuid());

        Assertions.assertThrows(NotFoundException.class, () -> ruleService.getRule(ruleDetailDto.getUuid()));

    }

    @Test
    public void testConditionGroup() throws NotFoundException {
        RuleConditionGroupRequestDto conditionGroupRequestDto = new RuleConditionGroupRequestDto();
        conditionGroupRequestDto.setName("name");
        conditionGroupRequestDto.setResource(Resource.CERTIFICATE);
        Assertions.assertThrows(ValidationException.class, () -> ruleService.createConditionGroup(conditionGroupRequestDto));

        conditionGroupRequestDto.setConditions(List.of(conditionRequestDto));
        RuleConditionGroupDto conditionGroupDetailDto = ruleService.createConditionGroup(conditionGroupRequestDto);
        Assertions.assertNotNull(conditionGroupDetailDto);

        UpdateRuleConditionGroupRequestDto updateConditionGroupRequestDto = new UpdateRuleConditionGroupRequestDto();
        updateConditionGroupRequestDto.setDescription("description");
        updateConditionGroupRequestDto.setConditions(conditionGroupRequestDto.getConditions());
        Assertions.assertEquals("description", ruleService.updateConditionGroup(conditionGroupDetailDto.getUuid(), updateConditionGroupRequestDto).getDescription());

        Assertions.assertNotNull(ruleService.getConditionGroup(conditionGroupDetailDto.getUuid()));

        Assertions.assertNotEquals(0, ruleService.listConditionGroups(null).size());
        ruleService.deleteConditionGroup(conditionGroupDetailDto.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> ruleService.getConditionGroup(conditionGroupDetailDto.getUuid()));
    }

    @Test
    public void testRuleTrigger() throws NotFoundException {
        RuleTriggerRequestDto triggerRequestDto = new RuleTriggerRequestDto();
        triggerRequestDto.setName("name");
        triggerRequestDto.setResource(Resource.CERTIFICATE);
        triggerRequestDto.setTriggerType(RuleTriggerType.EVENT);
        Assertions.assertThrows(ValidationException.class, () -> ruleService.createTrigger(triggerRequestDto));

        triggerRequestDto.setActions(List.of(actionRequestDto));
        RuleTriggerDetailDto triggerDetailDto = ruleService.createTrigger(triggerRequestDto);
        Assertions.assertNotNull(triggerDetailDto);

        UpdateRuleTriggerRequestDto updateTriggerRequestDto = new UpdateRuleTriggerRequestDto();
        updateTriggerRequestDto.setDescription("description");
        updateTriggerRequestDto.setTriggerType(RuleTriggerType.EVENT);
        updateTriggerRequestDto.setActions(triggerRequestDto.getActions());
        Assertions.assertEquals("description", ruleService.updateTrigger(triggerDetailDto.getUuid(), updateTriggerRequestDto).getDescription());

        Assertions.assertNotNull(ruleService.getTrigger(triggerDetailDto.getUuid()));

        Assertions.assertNotEquals(0, ruleService.listTriggers(null, null).size());

        ruleService.deleteTrigger(triggerDetailDto.getUuid());

        Assertions.assertThrows(NotFoundException.class, () -> ruleService.getTrigger(triggerDetailDto.getUuid()));

    }


    @Test
    public void testActionGroup() throws NotFoundException {
        RuleActionGroupRequestDto actionGroupRequestDto = new RuleActionGroupRequestDto();
        actionGroupRequestDto.setName("name");
        actionGroupRequestDto.setResource(Resource.CERTIFICATE);
        Assertions.assertThrows(ValidationException.class, () -> ruleService.createActionGroup(actionGroupRequestDto));
        actionGroupRequestDto.setActions(List.of(actionRequestDto));
        RuleActionGroupDto actionGroupDetailDto = ruleService.createActionGroup(actionGroupRequestDto);
        Assertions.assertNotNull(actionGroupDetailDto);

        UpdateRuleActionGroupRequestDto updateActionGroupRequestDto = new UpdateRuleActionGroupRequestDto();
        updateActionGroupRequestDto.setDescription("description");
        updateActionGroupRequestDto.setActions(actionGroupRequestDto.getActions());
        Assertions.assertEquals("description", ruleService.updateActionGroup(actionGroupDetailDto.getUuid(), updateActionGroupRequestDto).getDescription());

        Assertions.assertNotNull(ruleService.getActionGroup(actionGroupDetailDto.getUuid()));

        Assertions.assertNotEquals(0, ruleService.listActionGroups(null).size());
        ruleService.deleteActionGroup(actionGroupDetailDto.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> ruleService.getActionGroup(actionGroupDetailDto.getUuid()));
    }


}

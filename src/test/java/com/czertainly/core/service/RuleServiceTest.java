package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
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
    ConditionItemRequestDto conditionRequestDto;
    ExecutionItemRequestDto actionRequestDto;


    @BeforeEach
    public void setUp() {

        conditionRequestDto = new ConditionItemRequestDto();
        conditionRequestDto.setFieldSource(FilterFieldSource.PROPERTY);
        conditionRequestDto.setFieldIdentifier("identifier");
        conditionRequestDto.setOperator(FilterConditionOperator.EQUALS);
        conditionRequestDto.setValue(123);

        actionRequestDto = new ExecutionItemRequestDto();
        actionRequestDto.setActionType(ExecutionType.SET_FIELD);


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
        ConditionRequestDto conditionGroupRequestDto = new ConditionRequestDto();
        conditionGroupRequestDto.setName("name");
        conditionGroupRequestDto.setResource(Resource.CERTIFICATE);
        Assertions.assertThrows(ValidationException.class, () -> ruleService.createCondition(conditionGroupRequestDto));

        conditionGroupRequestDto.setItems(List.of(conditionRequestDto));
        ConditionDto conditionGroupDetailDto = ruleService.createCondition(conditionGroupRequestDto);
        Assertions.assertNotNull(conditionGroupDetailDto);

        UpdateConditionRequestDto updateConditionGroupRequestDto = new UpdateConditionRequestDto();
        updateConditionGroupRequestDto.setDescription("description");
        updateConditionGroupRequestDto.setItems(conditionGroupRequestDto.getItems());
        Assertions.assertEquals("description", ruleService.updateCondition(conditionGroupDetailDto.getUuid(), updateConditionGroupRequestDto).getDescription());

        Assertions.assertNotNull(ruleService.getCondition(conditionGroupDetailDto.getUuid()));

        Assertions.assertNotEquals(0, ruleService.listConditions(null).size());
        ruleService.deleteCondition(conditionGroupDetailDto.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> ruleService.getCondition(conditionGroupDetailDto.getUuid()));
    }

    @Test
    public void testRuleTrigger() throws NotFoundException {
        TriggerRequestDto triggerRequestDto = new TriggerRequestDto();
        triggerRequestDto.setName("name");
        triggerRequestDto.setResource(Resource.CERTIFICATE);
        triggerRequestDto.setType(TriggerType.EVENT);
        Assertions.assertThrows(ValidationException.class, () -> ruleService.createTrigger(triggerRequestDto));

        triggerRequestDto.setActions(List.of(actionRequestDto));
        TriggerDetailDto triggerDetailDto = ruleService.createTrigger(triggerRequestDto);
        Assertions.assertNotNull(triggerDetailDto);

        UpdateTriggerRequestDto updateTriggerRequestDto = new UpdateTriggerRequestDto();
        updateTriggerRequestDto.setDescription("description");
        updateTriggerRequestDto.setType(TriggerType.EVENT);
        updateTriggerRequestDto.setActions(triggerRequestDto.getActions());
        Assertions.assertEquals("description", ruleService.updateTrigger(triggerDetailDto.getUuid(), updateTriggerRequestDto).getDescription());

        Assertions.assertNotNull(ruleService.getTrigger(triggerDetailDto.getUuid()));

        Assertions.assertNotEquals(0, ruleService.listTriggers(null, null).size());

        ruleService.deleteTrigger(triggerDetailDto.getUuid());

        Assertions.assertThrows(NotFoundException.class, () -> ruleService.getTrigger(triggerDetailDto.getUuid()));

    }


    @Test
    public void testActionGroup() throws NotFoundException {
        ExecutionRequestDto actionGroupRequestDto = new ExecutionRequestDto();
        actionGroupRequestDto.setName("name");
        actionGroupRequestDto.setResource(Resource.CERTIFICATE);
        Assertions.assertThrows(ValidationException.class, () -> ruleService.createExecution(actionGroupRequestDto));
        actionGroupRequestDto.setItems(List.of(actionRequestDto));
        ActionDto actionGroupDetailDto = ruleService.createExecution(actionGroupRequestDto);
        Assertions.assertNotNull(actionGroupDetailDto);

        UpdateExecutionRequestDto updateActionGroupRequestDto = new UpdateExecutionRequestDto();
        updateActionGroupRequestDto.setDescription("description");
        updateActionGroupRequestDto.setItems(actionGroupRequestDto.getItems());
        Assertions.assertEquals("description", ruleService.updateExecution(actionGroupDetailDto.getUuid(), updateActionGroupRequestDto).getDescription());

        Assertions.assertNotNull(ruleService.getExecution(actionGroupDetailDto.getUuid()));

        Assertions.assertNotEquals(0, ruleService.listExecutions(null).size());
        ruleService.deleteExecution(actionGroupDetailDto.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> ruleService.getExecution(actionGroupDetailDto.getUuid()));
    }


}

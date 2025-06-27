package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.notification.NotificationProfileDetailDto;
import com.czertainly.api.model.client.notification.NotificationProfileRequestDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("java:S5778")
class TriggerServiceTest extends BaseSpringBootTest {

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    RuleService ruleService;

    @Autowired
    private ActionService actionService;

    @Autowired
    private TriggerService triggerService;

    @Autowired
    private NotificationProfileService notificationProfileService;

    private CustomAttribute domainAttr;
    private NotificationProfileDetailDto notificationProfile;

    @BeforeEach
    void setUp() throws AttributeException, NotFoundException, AlreadyExistException {
        domainAttr = new CustomAttribute();
        domainAttr.setUuid(UUID.randomUUID().toString());
        domainAttr.setName("domain");
        domainAttr.setType(AttributeType.CUSTOM);
        domainAttr.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties customProps = new CustomAttributeProperties();
        customProps.setLabel("Domain of resource");
        domainAttr.setProperties(customProps);
        attributeEngine.updateCustomAttributeDefinition(domainAttr, List.of(Resource.CERTIFICATE, Resource.DISCOVERY));

        NotificationProfileRequestDto requestDto = new NotificationProfileRequestDto();
        requestDto.setName("TestProfile");
        requestDto.setRecipientType(RecipientType.NONE);
        requestDto.setRepetitions(1);
        requestDto.setInternalNotification(true);
        notificationProfile = notificationProfileService.createNotificationProfile(requestDto);
    }

    @Test
    void testCreateTrigger() throws NotFoundException, AlreadyExistException {
        // create trigger
        TriggerRequestDto triggerRequest = new TriggerRequestDto();
        triggerRequest.setType(TriggerType.EVENT);
        triggerRequest.setEvent(ResourceEvent.CERTIFICATE_DISCOVERED);

        Assertions.assertThrows(ValidationException.class, () -> triggerService.createTrigger(triggerRequest), "Creating trigger without name should fail");

        triggerRequest.setName("DiscoveryCertificatesCategorization");
        Assertions.assertThrows(ValidationException.class, () -> triggerService.createTrigger(triggerRequest), "Creating trigger without resource should fail");

        triggerRequest.setResource(Resource.CERTIFICATE);
        Assertions.assertThrows(ValidationException.class, () -> triggerService.createTrigger(triggerRequest), "Creating trigger without actions should fail");

        // create execution
        ExecutionItemRequestDto executionItemRequest = new ExecutionItemRequestDto();
        executionItemRequest.setFieldSource(FilterFieldSource.CUSTOM);
        executionItemRequest.setFieldIdentifier("%s|%s".formatted(domainAttr.getName(), domainAttr.getContentType().name()));
        executionItemRequest.setData("CZ");

        ExecutionRequestDto executionRequest = new ExecutionRequestDto();
        executionRequest.setName("CategorizeCertificatesExecution");
        executionRequest.setResource(Resource.CERTIFICATE);
        executionRequest.setType(ExecutionType.SET_FIELD);
        executionRequest.setItems(List.of(executionItemRequest));
        ExecutionDto execution = actionService.createExecution(executionRequest);

        // create action
        ActionRequestDto actionRequest = new ActionRequestDto();
        actionRequest.setName("CategorizeCertificatesAction");
        actionRequest.setResource(Resource.CERTIFICATE);
        actionRequest.setExecutionsUuids(List.of(execution.getUuid()));
        ActionDetailDto action = actionService.createAction(actionRequest);

        triggerRequest.setActionsUuids(List.of(action.getUuid()));
        triggerRequest.setIgnoreTrigger(true);
        Assertions.assertThrows(ValidationException.class, () -> triggerService.createTrigger(triggerRequest), "Creating ignore trigger with actions should fail");

        triggerRequest.setIgnoreTrigger(false);
        TriggerDetailDto triggerDetailDto = triggerService.createTrigger(triggerRequest);

        UpdateTriggerRequestDto update = new UpdateTriggerRequestDto();
        update.setType(TriggerType.EVENT);
        update.setResource(Resource.CERTIFICATE);

        Assertions.assertThrows(ValidationException.class, () -> triggerService.updateTrigger(triggerDetailDto.getUuid(), update));

        // create execution with send notification type
        executionItemRequest = new ExecutionItemRequestDto();
        executionItemRequest.setNotificationProfileUuid(notificationProfile.getUuid());

        executionRequest = new ExecutionRequestDto();
        executionRequest.setName("SendNotification");
        executionRequest.setResource(Resource.CERTIFICATE);
        executionRequest.setType(ExecutionType.SEND_NOTIFICATION);
        executionRequest.setItems(List.of(executionItemRequest));
        execution = actionService.createExecution(executionRequest);

        UpdateActionRequestDto updateActionRequestDto = new UpdateActionRequestDto();
        updateActionRequestDto.setExecutionsUuids(List.of(execution.getUuid()));
        actionService.updateAction(action.getUuid(), updateActionRequestDto);
    }

    @Test
    void testWorkflowsResourcesValidation() throws AlreadyExistException, NotFoundException {
        // check validation of rules & conditions
        Assertions.assertThrows(ValidationException.class, () -> createCondition(Resource.ANY, ConditionType.CHECK_FIELD), "Cannot create condition of check field for Any resource");
        ConditionDto conditionCert = createCondition(Resource.CERTIFICATE, ConditionType.CHECK_FIELD);
        ConditionDto conditionDisc = createCondition(Resource.DISCOVERY, ConditionType.CHECK_FIELD);
        Assertions.assertEquals(1, ruleService.listConditions(Resource.CERTIFICATE).size());
        Assertions.assertEquals(2, ruleService.listConditions(Resource.ANY).size());

        Assertions.assertThrows(ValidationException.class, () -> createRule(Resource.CERTIFICATE, List.of(conditionCert.getUuid(), conditionDisc.getUuid())), "Cannot create rule with mixed resources");
        RuleDto ruleCert = createRule(Resource.CERTIFICATE, List.of(conditionCert.getUuid()));
        RuleDto ruleDisc = createRule(Resource.DISCOVERY, List.of(conditionDisc.getUuid()));
        RuleDto ruleMixed = createRule(Resource.ANY, List.of(conditionCert.getUuid(), conditionDisc.getUuid()));
        Assertions.assertEquals(2, ruleService.listRules(Resource.CERTIFICATE).size());
        Assertions.assertEquals(3, ruleService.listRules(Resource.ANY).size());

        // check validation of actions & executions
        Assertions.assertThrows(ValidationException.class, () -> createExecution(Resource.ANY, ExecutionType.SET_FIELD), "Cannot create condition of set field for Any resource");
        ExecutionDto executionCert = createExecution(Resource.CERTIFICATE, ExecutionType.SET_FIELD);
        ExecutionDto executionDisc = createExecution(Resource.DISCOVERY, ExecutionType.SET_FIELD);
        ExecutionDto executionAny = createExecution(Resource.ANY, ExecutionType.SEND_NOTIFICATION);
        Assertions.assertEquals(2, actionService.listExecutions(Resource.CERTIFICATE).size());
        Assertions.assertEquals(3, actionService.listExecutions(Resource.ANY).size());

        Assertions.assertThrows(ValidationException.class, () -> createAction(Resource.CERTIFICATE, List.of(executionCert.getUuid(), executionDisc.getUuid(), executionAny.getUuid())), "Cannot create action with mixed resources");
        ActionDto actionCert = createAction(Resource.CERTIFICATE, List.of(executionCert.getUuid()));
        ActionDto actionDisc = createAction(Resource.DISCOVERY, List.of(executionDisc.getUuid()));
        ActionDto actionMixed = createAction(Resource.ANY, List.of(executionCert.getUuid(), executionDisc.getUuid(), executionAny.getUuid()));
        ActionDto actionAny = createAction(Resource.ANY, List.of(executionAny.getUuid()));
        Assertions.assertEquals(3, actionService.listActions(Resource.CERTIFICATE).size());
        Assertions.assertEquals(4, actionService.listActions(Resource.ANY).size());

        // check validation of triggers
        Assertions.assertThrows(ValidationException.class, () -> createTrigger(Resource.ANY, null, null, null), "Creating trigger with resource Any should fail");
        Assertions.assertThrows(ValidationException.class, () -> createTrigger(Resource.CERTIFICATE, ResourceEvent.APPROVAL_CLOSED, null, null), "Creating trigger with mismatching resource of trigger and event should fail");
        Assertions.assertThrows(ValidationException.class, () -> createTrigger(Resource.CERTIFICATE, null, List.of(ruleCert.getUuid(), ruleDisc.getUuid()), List.of(actionCert.getUuid(), actionDisc.getUuid())), "Creating trigger with mismatching resource of rules should fail");
        Assertions.assertThrows(ValidationException.class, () -> createTrigger(Resource.CERTIFICATE, null, List.of(ruleCert.getUuid(), ruleMixed.getUuid()), List.of(actionCert.getUuid(), actionDisc.getUuid())), "Creating trigger with mismatching resource of rules should fail");
        Assertions.assertThrows(ValidationException.class, () -> createTrigger(Resource.CERTIFICATE, null, List.of(ruleCert.getUuid()), List.of(actionCert.getUuid(), actionDisc.getUuid())), "Creating trigger with mismatching resource of actions should fail");
        Assertions.assertThrows(ValidationException.class, () -> createTrigger(Resource.CERTIFICATE, ResourceEvent.CERTIFICATE_STATUS_CHANGED, List.of(ruleCert.getUuid()), List.of(actionCert.getUuid(), actionMixed.getUuid())));
        Assertions.assertDoesNotThrow(() -> createTrigger(Resource.CERTIFICATE, ResourceEvent.CERTIFICATE_STATUS_CHANGED, List.of(ruleCert.getUuid()), List.of(actionCert.getUuid(), actionAny.getUuid())));

        TriggerDto triggerWithEvent = createTrigger(Resource.CERTIFICATE, ResourceEvent.CERTIFICATE_STATUS_CHANGED, List.of(ruleCert.getUuid()), List.of(actionCert.getUuid()));
        Assertions.assertThrows(ValidationException.class,
                () -> triggerService.createTriggerAssociations(ResourceEvent.CERTIFICATE_EXPIRING, null, null, List.of(UUID.fromString(triggerWithEvent.getUuid())), true),
                "Creating trigger association with mismatching event should fail");
    }

    private ConditionDto createCondition(Resource resource, ConditionType type) throws AlreadyExistException {
        ConditionItemRequestDto conditionItemRequestDto = new ConditionItemRequestDto();
        conditionItemRequestDto.setFieldSource(FilterFieldSource.CUSTOM);
        conditionItemRequestDto.setFieldIdentifier("%s|%s".formatted(domainAttr.getName(), domainAttr.getContentType().name()));
        conditionItemRequestDto.setOperator(FilterConditionOperator.EQUALS);
        conditionItemRequestDto.setValue("CZ");

        ConditionRequestDto conditionRequestDto = new ConditionRequestDto();
        conditionRequestDto.setName("Test-%s-%s-condition".formatted(resource.getCode(), type.getCode()));
        conditionRequestDto.setResource(resource);
        conditionRequestDto.setType(type);
        conditionRequestDto.setItems(List.of(conditionItemRequestDto));

        return ruleService.createCondition(conditionRequestDto);
    }

    private RuleDto createRule(Resource resource, List<String> conditionsUuids) throws NotFoundException, AlreadyExistException {
        RuleRequestDto ruleRequestDto = new RuleRequestDto();
        ruleRequestDto.setName("Test-%s-rule".formatted(resource.getCode()));
        ruleRequestDto.setResource(resource);
        ruleRequestDto.setConditionsUuids(conditionsUuids);

        return ruleService.createRule(ruleRequestDto);
    }

    private ExecutionDto createExecution(Resource resource, ExecutionType type) throws NotFoundException, AlreadyExistException {
        ExecutionItemRequestDto executionItemRequest = new ExecutionItemRequestDto();
        if (type == ExecutionType.SET_FIELD) {
            executionItemRequest.setFieldSource(FilterFieldSource.CUSTOM);
            executionItemRequest.setFieldIdentifier("%s|%s".formatted(domainAttr.getName(), domainAttr.getContentType().name()));
            executionItemRequest.setData("CZ");
        } else {
            executionItemRequest.setNotificationProfileUuid(notificationProfile.getUuid());
        }

        ExecutionRequestDto executionRequest = new ExecutionRequestDto();
        executionRequest.setName("Test-%s-%s-execution".formatted(resource.getCode(), type.getCode()));
        executionRequest.setResource(resource);
        executionRequest.setType(type);
        executionRequest.setItems(List.of(executionItemRequest));

        return actionService.createExecution(executionRequest);
    }

    private ActionDto createAction(Resource resource, List<String> executionsUuids) throws NotFoundException, AlreadyExistException {
        ActionRequestDto actionRequest = new ActionRequestDto();
        actionRequest.setName("Test-%s-action-%s".formatted(resource.getCode(), UUID.randomUUID().toString()));
        actionRequest.setResource(resource);
        actionRequest.setExecutionsUuids(executionsUuids);

        return actionService.createAction(actionRequest);
    }

    private TriggerDto createTrigger(Resource resource, ResourceEvent event, List<String> rulesUuids, List<String> actionsUuids) throws NotFoundException, AlreadyExistException {
        TriggerRequestDto triggerRequest = new TriggerRequestDto();
        triggerRequest.setName("Test-%s-trigger".formatted(resource.getCode()));
        triggerRequest.setResource(resource);
        if (event != null) {
            triggerRequest.setType(TriggerType.EVENT);
            triggerRequest.setEvent(event);
        }
        triggerRequest.setRulesUuids(rulesUuids);
        triggerRequest.setActionsUuids(actionsUuids);

        return triggerService.createTrigger(triggerRequest);
    }
}

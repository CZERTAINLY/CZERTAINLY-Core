package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class TriggerServiceTest extends BaseSpringBootTest {

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private ActionService actionService;

    @Autowired
    private TriggerService triggerService;

    @Test
    void testCreateTrigger() throws AttributeException, NotFoundException, AlreadyExistException {
        // create trigger
        TriggerRequestDto triggerRequest = new TriggerRequestDto();
        triggerRequest.setType(TriggerType.EVENT);

        Assertions.assertThrows(ValidationException.class, () -> triggerService.createTrigger(triggerRequest), "Creating trigger without name should fail");

        triggerRequest.setName("DiscoveryCertificatesCategorization");
        Assertions.assertThrows(ValidationException.class, () -> triggerService.createTrigger(triggerRequest), "Creating trigger without resource should fail");

        triggerRequest.setResource(Resource.CERTIFICATE);
        Assertions.assertThrows(ValidationException.class, () -> triggerService.createTrigger(triggerRequest), "Creating trigger without actions should fail");

        CustomAttribute certificateDomainAttr = new CustomAttribute();
        certificateDomainAttr.setUuid(UUID.randomUUID().toString());
        certificateDomainAttr.setName("domain");
        certificateDomainAttr.setType(AttributeType.CUSTOM);
        certificateDomainAttr.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties customProps = new CustomAttributeProperties();
        customProps.setLabel("Domain of certificate");
        certificateDomainAttr.setProperties(customProps);
        attributeEngine.updateCustomAttributeDefinition(certificateDomainAttr, List.of(Resource.CERTIFICATE));

        // create execution
        ExecutionItemRequestDto executionItemRequest = new ExecutionItemRequestDto();
        executionItemRequest.setFieldSource(FilterFieldSource.CUSTOM);
        executionItemRequest.setFieldIdentifier("%s|%s".formatted(certificateDomainAttr.getName(), certificateDomainAttr.getContentType().name()));
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
    }
}

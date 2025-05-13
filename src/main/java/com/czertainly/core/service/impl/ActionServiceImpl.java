package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepDto;
import com.czertainly.api.model.client.notification.NotificationProfileDetailDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.*;
import com.czertainly.core.dao.repository.workflows.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ActionService;
import com.czertainly.core.service.NotificationProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ActionServiceImpl implements ActionService {

    private ObjectMapper objectMapper;

    private ExecutionRepository executionRepository;
    private ExecutionItemRepository executionItemRepository;
    private ActionRepository actionRepository;
    private NotificationProfileService notificationProfileService;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setExecutionRepository(ExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Autowired
    public void setExecutionItemRepository(ExecutionItemRepository executionItemRepository) {
        this.executionItemRepository = executionItemRepository;
    }

    @Autowired
    public void setActionRepository(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Autowired
    public void setNotificationProfileService(NotificationProfileService notificationProfileService) {
        this.notificationProfileService = notificationProfileService;
    }


    //region Executions

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.LIST)
    public List<ExecutionDto> listExecutions(Resource resource) {
        if (resource == null) return executionRepository.findAll().stream().map(Execution::mapToDto).toList();
        return executionRepository.findAllByResource(resource).stream().map(Execution::mapToDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.DETAIL)
    public ExecutionDto getExecution(String executionUuid) throws NotFoundException {
        return executionRepository.findByUuid(SecuredUUID.fromString(executionUuid)).orElseThrow(() -> new NotFoundException(Execution.class, executionUuid)).mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.CREATE)
    public ExecutionDto createExecution(ExecutionRequestDto request) throws AlreadyExistException {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot create an execution without any execution items.");
        }
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (executionRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("Execution with same name already exists.");
        }

        Execution execution = new Execution();
        execution.setName(request.getName());
        execution.setDescription(request.getDescription());
        execution.setType(request.getType());
        execution.setResource(request.getResource());
        executionRepository.save(execution);
        execution.setItems(createExecutionItems(request.getItems(), execution));

        return execution.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.UPDATE)
    public ExecutionDto updateExecution(String executionUuid, UpdateExecutionRequestDto request) throws NotFoundException {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Cannot create an execution without any execution items.");
        }

        Execution execution = executionRepository.findByUuid(SecuredUUID.fromString(executionUuid)).orElseThrow(() -> new NotFoundException(Execution.class, executionUuid));
        executionItemRepository.deleteAll(execution.getItems());

        execution.setDescription(request.getDescription());
        execution.setItems(createExecutionItems(request.getItems(), execution));
        executionRepository.save(execution);

        return execution.mapToDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.DELETE)
    public void deleteExecution(String executionUuid) throws NotFoundException {
        Execution execution = executionRepository.findWithActionsByUuid(UUID.fromString(executionUuid)).orElseThrow(() -> new NotFoundException(Execution.class, executionUuid));

        // check if not associated to actions
        if (!execution.getActions().isEmpty()) {
            throw new ValidationException(String.format("Cannot delete execution %s. It is associated to following actions: %s.", execution.getName(), String.join(", ", execution.getActions().stream().map(Action::getName).toList())));
        }

        executionRepository.delete(execution);
    }

    private Set<ExecutionItem> createExecutionItems(List<ExecutionItemRequestDto> executionItemRequestDtos, Execution execution) {
        Set<ExecutionItem> executionItems = new HashSet<>();
        for (ExecutionItemRequestDto executionItemRequestDto : executionItemRequestDtos) {
            ExecutionItem executionItem = switch (execution.getType()) {
                case SET_FIELD -> createSetFieldExecutionItem(execution, executionItemRequestDto);
                case SEND_NOTIFICATION -> createSendNotificationExecutionItem(execution, executionItemRequestDto);
            };

            executionItemRepository.save(executionItem);
            executionItems.add(executionItem);
        }
        return executionItems;
    }

    private ExecutionItem createSetFieldExecutionItem(Execution execution, ExecutionItemRequestDto executionItemRequestDto) {
        if (executionItemRequestDto.getFieldSource() == null || executionItemRequestDto.getFieldIdentifier() == null) {
            throw new ValidationException("Missing field source or field identifier in an execution.");
        }
        if (executionItemRequestDto.getFieldSource() != FilterFieldSource.PROPERTY && executionItemRequestDto.getFieldSource() != FilterFieldSource.CUSTOM) {
            throw new ValidationException("Missing field source or field identifier in an execution.");
        }

        ExecutionItem executionItem = new ExecutionItem();
        executionItem.setExecution(execution);
        executionItem.setFieldSource(executionItemRequestDto.getFieldSource());
        executionItem.setFieldIdentifier(executionItemRequestDto.getFieldIdentifier());
        if (executionItem.getFieldSource() != FilterFieldSource.CUSTOM) {
            executionItem.setData(executionItemRequestDto.getData());
        } else {
            try {
                if (executionItemRequestDto.getData() == null) {
                    executionItem.setData(new ArrayList<BaseAttributeContent<?>>());
                } else {
                    AttributeContentType attributeContentType = AttributeContentType.valueOf(executionItemRequestDto.getFieldIdentifier().substring(executionItemRequestDto.getFieldIdentifier().indexOf("|") + 1));
                    List<BaseAttributeContent<?>> contentItems = AttributeDefinitionUtils.createAttributeContentFromString(attributeContentType, executionItemRequestDto.getData() instanceof ArrayList<?> ? (List<String>) executionItemRequestDto.getData() : List.of(executionItemRequestDto.getData().toString()));
                    executionItem.setData(contentItems);
                }
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Unknown content type for custom attribute with field identifier: " + executionItemRequestDto.getFieldIdentifier());
            }
        }

        return executionItem;
    }

    private ExecutionItem createSendNotificationExecutionItem(Execution execution, ExecutionItemRequestDto executionItemRequestDto) {
        NameAndUuidDto notificationProfileInfo = null;
        try {
            notificationProfileInfo = objectMapper.convertValue(executionItemRequestDto.getData(), NameAndUuidDto.class);
        } catch (IllegalArgumentException e) {
            // requested with UUID only
        }

        if (notificationProfileInfo == null) {
            // try parsing data as UUID now
            try {
                UUID notificationProfileUuid = UUID.fromString(executionItemRequestDto.getData().toString());
                NotificationProfileDetailDto notificationProfile = notificationProfileService.getNotificationProfile(SecuredUUID.fromUUID(notificationProfileUuid), null);
                notificationProfileInfo = new NameAndUuidDto(notificationProfile.getUuid(), notificationProfile.getName());
            } catch (IllegalArgumentException | NotFoundException e) {
                throw new ValidationException("Invalid data of Send notification execution. Data should contain UUID of existing notification profile");
            }
        }

        ExecutionItem executionItem = new ExecutionItem();
        executionItem.setExecution(execution);
        executionItem.setData(notificationProfileInfo);

        return executionItem;
    }

    //endregion

    //region Actions

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.LIST)
    public List<ActionDto> listActions(Resource resource) {
        if (resource == null) return actionRepository.findAll().stream().map(Action::mapToDto).toList();
        return actionRepository.findAllByResource(resource).stream().map(Action::mapToDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.DETAIL)
    public ActionDetailDto getAction(String actionUuid) throws NotFoundException {
        return actionRepository.findByUuid(SecuredUUID.fromString(actionUuid)).orElseThrow(() -> new NotFoundException(Action.class, actionUuid)).mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.CREATE)
    public ActionDetailDto createAction(ActionRequestDto request) throws AlreadyExistException, NotFoundException {
        if (request.getName() == null) {
            throw new ValidationException("Property name cannot be empty.");
        }
        if (request.getResource() == null) {
            throw new ValidationException("Property resource cannot be empty.");
        }

        if (actionRepository.existsByName(request.getName())) {
            throw new AlreadyExistException("Action with same name already exists.");
        }

        if (request.getExecutionsUuids().isEmpty()) {
            throw new ValidationException("Action has to contain at least one execution.");
        }

        Action action = new Action();
        Set<Execution> executions = new HashSet<>();

        for (String executionUuid : request.getExecutionsUuids()) {
            Execution execution = executionRepository.findByUuid(SecuredUUID.fromString(executionUuid)).orElseThrow(() -> new NotFoundException(Execution.class, executionUuid));
            if (execution.getResource() != request.getResource()) {
                throw new ValidationException("Resource of execution with UUID " + executionUuid + " does not match rule resource.");
            }
            executions.add(execution);
        }

        action.setName(request.getName());
        action.setDescription(request.getDescription());
        action.setResource(request.getResource());
        action.setExecutions(executions);

        actionRepository.save(action);
        return action.mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.UPDATE)
    public ActionDetailDto updateAction(String actionUuid, UpdateActionRequestDto request) throws NotFoundException {
        if (request.getExecutionsUuids().isEmpty()) {
            throw new ValidationException("Action has to contain at least one execution.");
        }

        Set<Execution> executions = new HashSet<>();
        Action action = actionRepository.findByUuid(SecuredUUID.fromString(actionUuid)).orElseThrow(() -> new NotFoundException(Action.class, actionUuid));

        for (String executionUuid : request.getExecutionsUuids()) {
            Execution execution = executionRepository.findByUuid(SecuredUUID.fromString(executionUuid)).orElseThrow(() -> new NotFoundException(Execution.class, executionUuid));
            if (execution.getResource() != action.getResource()) {
                throw new ValidationException("Resource of execution with UUID " + executionUuid + " does not match rule resource.");
            }
            executions.add(execution);
        }

        action.setDescription(request.getDescription());
        action.setExecutions(executions);

        actionRepository.save(action);
        return action.mapToDetailDto();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ACTION, action = ResourceAction.DELETE)
    public void deleteAction(String actionUuid) throws NotFoundException {
        Action action = actionRepository.findWithTriggersByUuid(UUID.fromString(actionUuid)).orElseThrow(() -> new NotFoundException(Action.class, actionUuid));

        // check if not associated to triggers
        if (!action.getTriggers().isEmpty()) {
            throw new ValidationException(String.format("Cannot delete action %s. It is associated to following triggers: %s.", action.getName(), String.join(", ", action.getTriggers().stream().map(Trigger::getName).toList())));
        }

        actionRepository.delete(action);
    }

    //endregion
}

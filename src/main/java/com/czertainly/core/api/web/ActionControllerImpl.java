package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.ActionController;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.service.ActionService;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ActionControllerImpl implements ActionController {

    private ActionService actionService;

    @Autowired
    public void setActionService(ActionService actionService) {
        this.actionService = actionService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
    }

    @Override
    public List<ExecutionDto> listExecutions(Resource resource) {
        return actionService.listExecutions(resource);
    }

    @Override
    public ExecutionDto createExecution(ExecutionRequestDto request) {
        return actionService.createExecution(request);
    }

    @Override
    public ExecutionDto getExecution(String executionUuid) throws NotFoundException {
        return actionService.getExecution(executionUuid);
    }

    @Override
    public ExecutionDto updateExecution(String executionUuid, UpdateExecutionRequestDto request) throws NotFoundException {
        return actionService.updateExecution(executionUuid, request);
    }

    @Override
    public void deleteExecution(String executionUuid) throws NotFoundException {
        actionService.deleteExecution(executionUuid);
    }

    @Override
    public List<ActionDto> listActions(Resource resource) {
        actionService.listActions(resource);
    }

    @Override
    public ActionDetailDto createAction(ActionRequestDto request) {
        return actionService.createAction(request);
    }

    @Override
    public ActionDetailDto getAction(String actionUuid) throws NotFoundException {
        return actionService.getAction(actionUuid);
    }

    @Override
    public ActionDetailDto updateAction(String actionUuid, UpdateActionRequestDto request) throws NotFoundException {
        return actionService.updateAction(actionUuid, request);
    }

    @Override
    public void deleteAction(String actionUuid) throws NotFoundException {
        actionService.deleteAction(actionUuid);
    }
}

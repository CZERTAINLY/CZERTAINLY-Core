package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.service.ActionService;

import java.util.List;

public class ActionServiceImpl implements ActionService {
    @Override
    public List<ExecutionDto> listExecutions(Resource resource) {
        return List.of();
    }

    @Override
    public ExecutionDto createExecution(ExecutionRequestDto request) {
        return null;
    }

    @Override
    public ExecutionDto getExecution(String executionUuid) throws NotFoundException {
        return null;
    }

    @Override
    public ExecutionDto updateExecution(String executionUuid, UpdateExecutionRequestDto request) throws NotFoundException {
        return null;
    }

    @Override
    public void deleteExecution(String executionUuid) throws NotFoundException {

    }

    @Override
    public List<ActionDto> listActions(Resource resource) {
        return List.of();
    }

    @Override
    public ActionDetailDto createAction(ActionRequestDto request) {
        return null;
    }

    @Override
    public ActionDetailDto getAction(String actionUuid) throws NotFoundException {
        return null;
    }

    @Override
    public ActionDetailDto updateAction(String actionUuid, UpdateActionRequestDto request) throws NotFoundException {
        return null;
    }

    @Override
    public void deleteAction(String actionUuid) throws NotFoundException {

    }
}

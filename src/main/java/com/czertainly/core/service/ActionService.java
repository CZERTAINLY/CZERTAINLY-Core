package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.entity.workflows.TriggerHistoryRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ActionService {

    List<ExecutionDto> listExecutions(Resource resource);
    ExecutionDto getExecution(String executionUuid) throws NotFoundException;
    ExecutionDto createExecution(ExecutionRequestDto request) throws AlreadyExistException;
    ExecutionDto updateExecution(String executionUuid, UpdateExecutionRequestDto request) throws NotFoundException;
    void deleteExecution(String executionUuid) throws NotFoundException;

    List<ActionDto> listActions(Resource resource);
    ActionDetailDto getAction(String actionUuid) throws NotFoundException;
    ActionDetailDto createAction(ActionRequestDto request) throws AlreadyExistException, NotFoundException;
    ActionDetailDto updateAction(String actionUuid, UpdateActionRequestDto request) throws NotFoundException;
    void deleteAction(String actionUuid) throws NotFoundException;

}

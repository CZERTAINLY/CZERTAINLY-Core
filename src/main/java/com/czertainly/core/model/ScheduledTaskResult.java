package com.czertainly.core.model;

import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import lombok.Data;

@Data
public class ScheduledTaskResult {

    private SchedulerJobExecutionStatus status;

    private String exceptionMessage;

    public ScheduledTaskResult(SchedulerJobExecutionStatus status) {
        this.status = status;
    }

    public ScheduledTaskResult(SchedulerJobExecutionStatus status, String exceptionMessage) {
        this.status = status;
        this.exceptionMessage = exceptionMessage;
    }
}

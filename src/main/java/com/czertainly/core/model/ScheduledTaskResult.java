package com.czertainly.core.model;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import lombok.Data;

@Data
public class ScheduledTaskResult {

    private SchedulerJobExecutionStatus status;
    private String resultMessage;
    private Resource resultObjectType;
    private String resultObjectIdentification;

    public ScheduledTaskResult(SchedulerJobExecutionStatus status) {
        this.status = status;
    }

    public ScheduledTaskResult(SchedulerJobExecutionStatus status, String resultMessage) {
        this.status = status;
        this.resultMessage = resultMessage;
    }

    public ScheduledTaskResult(SchedulerJobExecutionStatus status, String resultMessage, Resource resultObjectType, String resultObjectIdentification) {
        this.status = status;
        this.resultMessage = resultMessage;
        this.resultObjectType = resultObjectType;
        this.resultObjectIdentification = resultObjectIdentification;
    }
}

package com.czertainly.core.tasks;

import com.czertainly.api.clients.SchedulerApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.scheduler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class SchedulerJobProcessor {

    private Logger logger = LoggerFactory.getLogger(SchedulerJobProcessor.class);

    @Autowired
    SchedulerApiClient schedulerApiClient;

    abstract String getJobName();

    abstract String getCronExpression();

    abstract String getJobClassName();

    abstract SchedulerJobExecutionStatus performJob();

    protected boolean registerScheduler() {
        final SchedulerJobDetail schedulerDetail = new SchedulerJobDetail(getJobName(), getCronExpression(), getJobClassName());
        SchedulerResponseDto responseDto = null;
        try {
            responseDto = schedulerApiClient.schedulerCreate(new SchedulerRequestDto(schedulerDetail));
        } catch (ConnectorException e) {
            logger.error("Unable to register scheduler {}", getJobName());
        }
        return responseDto != null && SchedulerStatus.OK.equals(responseDto.getSchedulerStatus());
    }

    public void processTask(final Long jobID) throws ConnectorException {
        final SchedulerJobExecutionStatus result = performJob();
        schedulerApiClient.informJobHistory(new SchedulerJobHistory(jobID, result));
    }



}

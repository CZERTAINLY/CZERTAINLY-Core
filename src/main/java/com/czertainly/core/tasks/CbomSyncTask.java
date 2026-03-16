package com.czertainly.core.tasks;

import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.api.ScheduledJobSkippedExcetion;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.CbomService;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@NoArgsConstructor
@Transactional
public class CbomSyncTask implements ScheduledJobTask {

    public static final String NAME = "CbomSyncTask";
    private static final String CRON_EXPRESSION = "0 0 * ? * *";
    private static final Logger logger = LoggerFactory.getLogger(CbomSyncTask.class);

    private CbomService cbomService;

    @Autowired
    public void setCbomService(CbomService cbomService) {
        this.cbomService = cbomService;
    }

    public String getDefaultJobName() {
        return NAME;
    }

    public String getDefaultCronExpression() {
        return CRON_EXPRESSION;
    }

    public boolean isDefaultOneTimeJob() {
        return false;
    }

    public String getJobClassName() {
        return this.getClass().getName();
    }

    public boolean isSystemJob() {
        return true;
    }

    @Override
    public ScheduledTaskResult performJob(final ScheduledJobInfo scheduledJobInfo, final Object taskData) {
        if (!cbomService.isCbomRepositoryClientConfigured()) {
            return new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, "CBOM Sync: SKIPPED", Resource.CBOM, null);
        }

        String syncResultMessage;
        try {
            syncResultMessage = cbomService.sync();
        } catch (Exception e) {
            if (e instanceof CbomRepositoryException ex && ex.getProblemDetail() != null && ex.getProblemDetail().getStatus() == 404) {
                throw new ScheduledJobSkippedExcetion();
            }

            final String errorMessage = String.format("Unable to sync CBOMs for job %s. Error: %s", scheduledJobInfo == null ? "" : scheduledJobInfo.jobName(), e.getMessage());
            logger.error(errorMessage, e);
            return new ScheduledTaskResult(SchedulerJobExecutionStatus.FAILED, errorMessage, Resource.CBOM, null);
        }

        return new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, syncResultMessage, Resource.CBOM, null);
    }
}

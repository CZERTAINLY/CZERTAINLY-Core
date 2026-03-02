package com.czertainly.core.tasks;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.CbomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@Component
@NoArgsConstructor
@Transactional
public class CbomSyncTask implements ScheduledJobTask {

    private static final String CRON_EXPRESSION = "0 0 * ? * *";
    private static final Logger logger = LoggerFactory.getLogger(CbomSyncTask.class);

    private CbomService cbomService;

    @Autowired
    public void setCbomService(CbomService cbomService) {
        this.cbomService = cbomService;
    }

    public String getDefaultJobName() {
        return "CbomSyncTask";
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
        try {
            cbomService.sync();
        } catch (Exception e) {
            final String errorMessage = String.format("Unable to sync CBOMs for job %s. Error: %s", scheduledJobInfo == null ? "" : scheduledJobInfo.jobName(), e.getMessage());
            logger.error(errorMessage);
            return new ScheduledTaskResult(SchedulerJobExecutionStatus.FAILED, errorMessage, Resource.CBOM, null);
        }

        return new ScheduledTaskResult(
            SchedulerJobExecutionStatus.SUCCESS,
            null,
            Resource.CBOM,
            null
        );
    }
}

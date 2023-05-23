package com.czertainly.core.tasks;

import com.czertainly.api.clients.SchedulerApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.scheduler.*;
import com.czertainly.core.dao.entity.ScheduledJob;
import com.czertainly.core.dao.entity.ScheduledJobHistory;
import com.czertainly.core.dao.repository.ScheduledJobHistoryRepository;
import com.czertainly.core.dao.repository.ScheduledJobsRepository;
import com.czertainly.core.security.authn.CzertainlyUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.UUID;

public abstract class SchedulerJobProcessor {

    private Logger logger = LoggerFactory.getLogger(SchedulerJobProcessor.class);

    @Autowired
    SchedulerApiClient schedulerApiClient;

    @Autowired
    ScheduledJobsRepository scheduledJobsRepository;

    @Autowired
    ScheduledJobHistoryRepository scheduledJobHistoryRepository;

    abstract String getDefaultJobName();

    abstract String getDefaultCronExpression();

    abstract String getJobClassName();

    abstract boolean systemJob();

    abstract SchedulerJobExecutionStatus performJob(final String jobName);

    public boolean registerScheduler() {
        return registerScheduler(getDefaultJobName(), getDefaultCronExpression());
    }

    public boolean registerScheduler(final String jobName, final String cronExpression) {
        return registerScheduler(jobName, cronExpression, null);
    }

    public boolean registerScheduler(final String jobName, final String cronExpression, final Object objectData) {
        final SchedulerJobDto schedulerDetail = new SchedulerJobDto(jobName, cronExpression, getJobClassName());
        SchedulerResponseDto responseDto = null;
        try {
            responseDto = schedulerApiClient.schedulerCreate(new SchedulerRequestDto(schedulerDetail));
            if (SchedulerStatus.OK.equals(responseDto.getSchedulerStatus())) {
                saveJobDefinition(jobName, cronExpression, objectData);
            }
        } catch (ConnectorException e) {
            logger.error("Unable to register scheduler {}", getDefaultJobName());
        }
        return responseDto != null && SchedulerStatus.OK.equals(responseDto.getSchedulerStatus());
    }

    public void processTask(final String jobName) throws ConnectorException {
        final ScheduledJobHistory scheduledJobHistory = registerJobHistory(jobName);
        final SchedulerJobExecutionStatus result = performJob(jobName);
        updateJobHistory(scheduledJobHistory, result);
        checkOneShotJob(jobName, result);
    }

    private void checkOneShotJob(final String jobName, final SchedulerJobExecutionStatus status) throws ConnectorException {
        final ScheduledJob scheduledJob = scheduledJobsRepository.findByJobName(jobName);
        if (SchedulerJobExecutionStatus.SUCCESS.equals(status)
                && scheduledJob != null
                && scheduledJob.isOneShotOnly()) {
            schedulerApiClient.deleteScheduledJob(jobName);
            scheduledJobsRepository.deleteById(scheduledJob.getUuid());
            logger.info("Scheduled job {} was deleted/unscheduled because it was one shot job only.", jobName);
        }
    }

    private ScheduledJobHistory registerJobHistory(final String jobName) {
        final ScheduledJobHistory scheduledJobHistory = new ScheduledJobHistory();
        scheduledJobHistory.setJobName(jobName);
        scheduledJobHistory.setJobExecution(new Date());
        scheduledJobHistory.setSchedulerExecutionStatus(SchedulerJobExecutionStatus.UNKNOWN);
        return scheduledJobHistoryRepository.save(scheduledJobHistory);
    }

    private void updateJobHistory(final ScheduledJobHistory scheduledJobHistory, final SchedulerJobExecutionStatus status) {
        scheduledJobHistory.setJobEndTime(new Date());
        scheduledJobHistory.setSchedulerExecutionStatus(status);
        scheduledJobHistoryRepository.save(scheduledJobHistory);
    }

    private void saveJobDefinition(final String jobName, final String cronExpression, final Object objectData) {

        if (scheduledJobsRepository.findByJobName(jobName) != null) {
            logger.info("Job {} was already registered.", jobName);
            return;
        }

        final ScheduledJob scheduledJob = new ScheduledJob();
        scheduledJob.setJobName(jobName);
        scheduledJob.setCronExpression(cronExpression);
        scheduledJob.setObjectData(objectData);
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            final String userUuid = ((CzertainlyUserDetails) authentication.getPrincipal()).getUserUuid();
            scheduledJob.setUserUuid(UUID.fromString(userUuid));
        }
        scheduledJob.setEnabled(true);
        scheduledJob.setSystem(this.systemJob());
        scheduledJob.setJobClassName(this.getJobClassName());
        scheduledJobsRepository.save(scheduledJob);
        logger.info("Scheduler job {} was registered.", jobName);
    }


}

package com.czertainly.core.tasks;

import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HandleExpiringCertificatesTask implements ScheduledJobTask {

    private static final String JOB_NAME = "handleExpiringCertificateJob";
    //  At minute 30, every hour
    private static final String CRON_EXPRESSION = "0 30 * ? * *";


    private CertificateService certificateService;

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Override
    public String getDefaultJobName() {
        return JOB_NAME;
    }

    @Override
    public String getDefaultCronExpression() {
        return CRON_EXPRESSION;
    }

    @Override
    public boolean isDefaultOneTimeJob() {
        return false;
    }

    @Override
    public String getJobClassName() {
        return this.getClass().getName();
    }

    @Override
    public boolean isSystemJob() {
        return true;
    }

    @Override
    public ScheduledTaskResult performJob(ScheduledJobInfo scheduledJobInfo, Object taskData) {
        int certificatesHandled = certificateService.handleExpiringCertificates();
        return new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, "%s expiring certificates which do not have renewal have been handled.".formatted(certificatesHandled));
    }
}

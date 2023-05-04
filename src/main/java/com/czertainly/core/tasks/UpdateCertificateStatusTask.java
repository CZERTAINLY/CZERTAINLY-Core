package com.czertainly.core.tasks;

import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.service.CertificateService;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class UpdateCertificateStatusTask extends SchedulerJobProcessor{

    private static final String JOB_NAME = "updateCertificateStatusJob";
    private static  final String CRON_EXPRESSION = "0 * * ? * *"; //TODO lukas.rejha - need to be change on 0 0 * ? * *

    private CertificateService certificateService;

    @Override
    String getJobName() {
        return JOB_NAME;
    }

    @Override
    String getCronExpression() {
        return CRON_EXPRESSION;
    }

    @Override
    String getJobClassName() {
        return this.getClass().getName();
    }

    @Override
    SchedulerJobExecutionStatus performJob() {
        certificateService.updateCertificatesStatusScheduled();
        return SchedulerJobExecutionStatus.SUCCESS;
    }

    // SETTERs

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }
}

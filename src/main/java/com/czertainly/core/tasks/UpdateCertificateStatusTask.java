package com.czertainly.core.tasks;

import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.CertificateService;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class UpdateCertificateStatusTask extends SchedulerJobProcessor{

    private static final String JOB_NAME = "updateCertificateStatusJob";
    private static  final String CRON_EXPRESSION = "0 0 * ? * *";

    private CertificateService certificateService;

    @Override
    String getDefaultJobName() {
        return JOB_NAME;
    }

    @Override
    String getDefaultCronExpression() {
        return CRON_EXPRESSION;
    }

    @Override
    boolean isDefaultOneTimeJob() {
        return false;
    }

    @Override
    String getJobClassName() {
        return this.getClass().getName();
    }

    @Override
    boolean systemJob() {
        return true;
    }

    @Override
    @AuditLogged(originator = ObjectType.SCHEDULER, affected = ObjectType.CERTIFICATE, operation = OperationType.UPDATE)
    @Transactional
    // TODO: must be public or refactored, transaction should not block update of all certificates,
    //  maybe transaction should be used only for fetching the data, but update will run without it in background
    ScheduledTaskResult performJob(final String jobName) {
        int certificatesUpdated = certificateService.updateCertificatesStatusScheduled();
        return new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, String.format("Updated status of %d certificates", certificatesUpdated));
    }

    // SETTERs

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }
}

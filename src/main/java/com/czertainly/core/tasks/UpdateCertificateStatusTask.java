package com.czertainly.core.tasks;

import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.ApprovalService;
import com.czertainly.core.service.CertificateService;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
@Transactional
public class UpdateCertificateStatusTask implements ScheduledJobTask {

    private static final String JOB_NAME = "updateCertificateStatusJob";
    private static final String CRON_EXPRESSION = "0 0 * ? * *";

    private ApprovalService approvalService;
    private CertificateService certificateService;

    public String getDefaultJobName() {
        return JOB_NAME;
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

    @AuditLogged(originator = ObjectType.SCHEDULER, affected = ObjectType.CERTIFICATE, operation = OperationType.UPDATE)
    public ScheduledTaskResult performJob(final ScheduledJobInfo scheduledJobInfo, final Object taskData) {
        int certificatesUpdated = certificateService.updateCertificatesStatusScheduled();
        int expiredApprovals = approvalService.checkApprovalsExpiration();

        String message = "Updated status of %d certificate(s).".formatted(certificatesUpdated);
        if(expiredApprovals > 0) {
            message += " Expired %d approval(s).".formatted(expiredApprovals);
        }

        return new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, message);
    }

    // SETTERs

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setApprovalService(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

}

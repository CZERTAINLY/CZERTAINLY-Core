package com.czertainly.core.tasks;

import com.czertainly.api.exception.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ScheduledTasks {

    private UpdateCertificateStatusTask updateCertificateStatusTask;

    private UpdateIntuneRevocationRequestsTask updateIntuneRevocationRequestsTask;

    @Autowired
    public void setUpdateCertificateStatusTask(UpdateCertificateStatusTask updateCertificateStatusTask) {
        this.updateCertificateStatusTask = updateCertificateStatusTask;
    }

    @Autowired
    public void setUpdateIntuneRevocationRequestsTask(UpdateIntuneRevocationRequestsTask updateIntuneRevocationRequestsTask) {
        this.updateIntuneRevocationRequestsTask = updateIntuneRevocationRequestsTask;
    }

    @ConditionalOnProperty(value = "scheduled-tasks.enabled", matchIfMissing = true, havingValue = "true")
    public void registerJobs() throws SchedulerException {
        updateCertificateStatusTask.registerScheduler();
        updateIntuneRevocationRequestsTask.registerScheduler();
    }
}

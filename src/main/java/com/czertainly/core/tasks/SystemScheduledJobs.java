package com.czertainly.core.tasks;

import com.czertainly.api.exception.SchedulerException;
import com.czertainly.core.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SystemScheduledJobs {

    private SchedulerService schedulerService;

    @Autowired
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Bean
    @ConditionalOnProperty(value = "scheduled-tasks.enabled", matchIfMissing = true, havingValue = "true")
    public Void registerJobs() throws SchedulerException {
        schedulerService.registerScheduledJob(UpdateCertificateStatusTask.class);
        schedulerService.registerScheduledJob(UpdateIntuneRevocationRequestsTask.class);
        return null;
    }
}

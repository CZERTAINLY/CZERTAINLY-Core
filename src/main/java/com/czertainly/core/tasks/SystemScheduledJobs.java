package com.czertainly.core.tasks;

import com.czertainly.api.exception.SchedulerException;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.service.SchedulerService;
import com.czertainly.core.settings.SettingsCache;
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
        PlatformSettingsDto platformSettingsDto = SettingsCache.getSettings(SettingsSection.PLATFORM);
        if (platformSettingsDto.getCertificates() != null) {
            if (Boolean.TRUE.equals(platformSettingsDto.getCertificates().getValidationEnabled())) {
                String cronExpression = "0 0 00 1/%s * ? *".formatted(platformSettingsDto.getCertificates().getValidationFrequency());
                schedulerService.registerScheduledJob(UpdateCertificateStatusTask.class, new UpdateCertificateStatusTask().getDefaultJobName(), cronExpression, false, null);
            }
        } else {
            schedulerService.registerScheduledJob(UpdateCertificateStatusTask.class);
        }
        schedulerService.registerScheduledJob(UpdateIntuneRevocationRequestsTask.class);
        return null;
    }
}

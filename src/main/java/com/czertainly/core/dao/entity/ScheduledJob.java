package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.scheduler.ScheduledJobDetailDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ScheduledJob extends UniquelyIdentified{

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "object_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Object objectData;

    @Column(name = "user_uuid")
    private UUID userUuid;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "one_shot_only")
    private boolean oneShotOnly;

    @Column(name = "system")
    private boolean system;

    @Column(name = "job_class_name")
    private String jobClassName;

    public ScheduledJobDetailDto mapToDetailDto(ScheduledJobHistory latestHistory) {
        final ScheduledJobDetailDto dto = new ScheduledJobDetailDto();
        dto.setUuid(this.uuid);
        dto.setJobName(this.jobName);
        dto.setCronExpression(this.cronExpression);
        dto.setUserUuid(this.userUuid);
        dto.setEnabled(this.enabled);
        dto.setSystem(this.system);
        dto.setOneShotOnly(this.oneShotOnly);
        dto.setJobClassName(this.jobClassName);
        if(latestHistory != null) dto.setLastExecutionStatus(latestHistory.getSchedulerExecutionStatus());

        return dto;
    }

    public ScheduledJobDto mapToDto(ScheduledJobHistory latestHistory) {
        final ScheduledJobDto dto = new ScheduledJobDto();
        dto.setUuid(this.uuid);
        dto.setJobName(this.jobName);
        dto.setCronExpression(this.cronExpression);
        dto.setEnabled(this.enabled);
        dto.setOneShotOnly(this.oneShotOnly);
        if(latestHistory != null) dto.setLastExecutionStatus(latestHistory.getSchedulerExecutionStatus());

        return dto;
    }

}

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

    @Column(name = "one_time")
    private boolean oneTime;

    @Column(name = "system")
    private boolean system;

    @Column(name = "job_class_name")
    private String jobClassName;

    public ScheduledJobDetailDto mapToDetailDto(ScheduledJobHistory latestHistory) {
        String jobType = this.jobClassName.lastIndexOf(".") == -1 ? this.jobClassName : this.jobClassName.substring(this.jobClassName.lastIndexOf(".") + 1);

        final ScheduledJobDetailDto dto = new ScheduledJobDetailDto();
        dto.setUuid(this.uuid);
        dto.setJobName(this.jobName);
        dto.setJobType(jobType);
        dto.setCronExpression(this.cronExpression);
        dto.setUserUuid(this.userUuid);
        dto.setEnabled(this.enabled);
        dto.setSystem(this.system);
        dto.setOneTime(this.oneTime);
        if(latestHistory != null) dto.setLastExecutionStatus(latestHistory.getSchedulerExecutionStatus());

        return dto;
    }

    public ScheduledJobDto mapToDto(ScheduledJobHistory latestHistory) {
        String jobType = this.jobClassName.lastIndexOf(".") == -1 ? this.jobClassName : this.jobClassName.substring(this.jobClassName.lastIndexOf(".") + 1);

        final ScheduledJobDto dto = new ScheduledJobDto();
        dto.setUuid(this.uuid);
        dto.setJobName(this.jobName);
        dto.setJobType(jobType);
        dto.setCronExpression(this.cronExpression);
        dto.setEnabled(this.enabled);
        dto.setOneTime(this.oneTime);
        dto.setSystem(this.system);
        if(latestHistory != null) dto.setLastExecutionStatus(latestHistory.getSchedulerExecutionStatus());

        return dto;
    }

}

package com.czertainly.core.dao.entity;

import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.api.model.core.scheduler.ScheduledJobHistoryDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.util.Date;

@Data
@Entity
public class ScheduledJobHistory extends UniquelyIdentified {

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_execution")
    private Date jobExecution;

    @Column(name = "job_end_time")
    private Date jobEndTime;

    @Column(name = "scheduler_execution_status")
    @Enumerated(EnumType.STRING)
    private SchedulerJobExecutionStatus schedulerExecutionStatus;

    public ScheduledJobHistoryDto mapToDto() {
        final ScheduledJobHistoryDto schedulerJobHistoryDto = new ScheduledJobHistoryDto();
        schedulerJobHistoryDto.setJobUuid(this.uuid);
        schedulerJobHistoryDto.setStatus(this.schedulerExecutionStatus);
        schedulerJobHistoryDto.setStartTime(this.jobExecution);
        schedulerJobHistoryDto.setEndTime(this.jobEndTime);
        return schedulerJobHistoryDto;
    }


}

package com.czertainly.core.dao.entity;

import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.api.model.core.scheduler.ScheduledJobHistoryDto;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
public class ScheduledJobHistory extends UniquelyIdentified {

    @Column(name = "job_execution")
    private Date jobExecution;

    @Column(name = "job_end_time")
    private Date jobEndTime;

    @Column(name = "scheduler_execution_status")
    @Enumerated(EnumType.STRING)
    private SchedulerJobExecutionStatus schedulerExecutionStatus;

    @ManyToOne
    @JoinColumn(name = "scheduled_job_uuid", insertable = false, updatable = false)
    private ScheduledJob scheduledJob;

    @Column(name = "scheduled_job_uuid")
    private UUID scheduledJobUuid;

    @Column(name = "exception_message")
    private String exceptionMessage;

    public ScheduledJobHistoryDto mapToDto() {
        final ScheduledJobHistoryDto schedulerJobHistoryDto = new ScheduledJobHistoryDto();
        schedulerJobHistoryDto.setJobUuid(this.uuid);
        schedulerJobHistoryDto.setStatus(this.schedulerExecutionStatus);
        schedulerJobHistoryDto.setStartTime(this.jobExecution);
        schedulerJobHistoryDto.setEndTime(this.jobEndTime);
        schedulerJobHistoryDto.setErrorMessage(this.exceptionMessage);
        return schedulerJobHistoryDto;
    }


}

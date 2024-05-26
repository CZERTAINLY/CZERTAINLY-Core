package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.workflows.TriggerHistoryRecordDto;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "trigger_history_record")
public class TriggerHistoryRecord extends UniquelyIdentified {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_history_uuid", nullable = false)
    private TriggerHistory triggerHistory;

    @Column(name = "condition_uuid")
    private UUID conditionUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_uuid", insertable = false, updatable = false)
    private Condition condition;

    @Column(name = "execution_uuid")
    private UUID executionUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_uuid", insertable = false, updatable = false)
    private Execution execution;

    @Column(name = "message")
    private String message;

    public TriggerHistoryRecordDto mapToDto() {
        TriggerHistoryRecordDto triggerHistoryRecordDto = new TriggerHistoryRecordDto();
        triggerHistoryRecordDto.setMessage(message);
        if (executionUuid != null) triggerHistoryRecordDto.setExecution(execution.mapToDto());
        if (conditionUuid != null) triggerHistoryRecordDto.setCondition(condition.mapToDto());
        return triggerHistoryRecordDto;
    }
}

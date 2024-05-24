package com.czertainly.core.dao.entity.workflows;

import com.czertainly.api.model.core.workflows.TriggerHistoryDto;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "trigger_history")
public class TriggerHistory extends UniquelyIdentified {

    @Column(name = "trigger_uuid", nullable = false)
    private UUID triggerUuid;

    @Column(name = "trigger_association_object_uuid")
    private UUID triggerAssociationObjectUuid;

    @Column(name = "object_uuid")
    private UUID objectUuid;

    @Column(name = "reference_object_uuid")
    private UUID referenceObjectUuid;

    @Column(name = "conditions_matched", nullable = false)
    private boolean conditionsMatched;

    @Column(name = "actions_performed", nullable = false)
    private boolean actionsPerformed;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "message")
    private String message;

    @OneToMany(mappedBy = "triggerHistory", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<TriggerHistoryRecord> records = new ArrayList<>();

    public TriggerHistoryDto mapToDto() {
        TriggerHistoryDto triggerHistoryDto = new TriggerHistoryDto();
        triggerHistoryDto.setUuid(String.valueOf(uuid));
        triggerHistoryDto.setConditionsMatched(conditionsMatched);
        triggerHistoryDto.setActionsPerformed(actionsPerformed);
        if (objectUuid != null) triggerHistoryDto.setObjectUuid(objectUuid.toString());
        if (referenceObjectUuid != null) triggerHistoryDto.setReferenceObjectUuid(referenceObjectUuid.toString());
        triggerHistoryDto.setTriggeredAt(triggeredAt);
        triggerHistoryDto.setMessage(message);
        triggerHistoryDto.setRecords(records.stream().map(TriggerHistoryRecord::mapToDto).toList());
        return triggerHistoryDto;
    }

}

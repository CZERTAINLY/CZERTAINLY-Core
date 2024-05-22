package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.rules.RuleTriggerHistoryDto;
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
public class RuleTriggerHistory extends UniquelyIdentified {

    @Column(name = "trigger_uuid", nullable = false)
    private UUID triggerUuid;

    @Column(name = "trigger_association_uuid")
    private UUID triggerAssociationUuid;

    @Column(name = "conditions_matched", nullable = false)
    private boolean conditionsMatched;

    @Column(name = "actions_performed", nullable = false)
    private boolean actionsPerformed;

    @Column(name = "object_uuid")
    private UUID objectUuid;

    @Column(name = "reference_object_uuid")
    private UUID referenceObjectUuid;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "message")
    private String message;

    @OneToMany(mappedBy = "triggerHistory", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RuleTriggerHistoryRecord> records = new ArrayList<>();

    public RuleTriggerHistoryDto mapToDto() {
        RuleTriggerHistoryDto triggerHistoryDto = new RuleTriggerHistoryDto();
        triggerHistoryDto.setUuid(String.valueOf(uuid));
        triggerHistoryDto.setConditionsMatched(conditionsMatched);
        triggerHistoryDto.setActionsPerformed(actionsPerformed);
        if (objectUuid != null) triggerHistoryDto.setObjectUuid(String.valueOf(objectUuid));
        if (referenceObjectUuid != null) triggerHistoryDto.setReferenceObjectUuid(String.valueOf(referenceObjectUuid));
        triggerHistoryDto.setTriggeredAt(triggeredAt);
        triggerHistoryDto.setMessage(message);
        triggerHistoryDto.setRecords(records.stream().map(RuleTriggerHistoryRecord::mapToDto).toList());
        return triggerHistoryDto;
    }

}

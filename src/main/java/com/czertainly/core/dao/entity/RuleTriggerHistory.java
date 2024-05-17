package com.czertainly.core.dao.entity;

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

    @Column(name = "object_uuid", nullable = false)
    private UUID objectUuid;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "message")
    private String message;

    @OneToMany(mappedBy = "triggerHistory", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RuleTriggerHistoryRecord> triggerHistoryRecordList = new ArrayList<>();

}

package com.czertainly.core.dao.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

@Data
@Entity
@Table(name = "approval_recipient")
public class ApprovalRecipient extends UniquelyIdentified {

    @Column(name = "user_uuid")
    private UUID userUuid;

    @Column(name = "approval_step_uuid")
    private UUID approvalStepUuid;

    @ManyToOne
    @JoinColumn(name = "approval_step_uuid", insertable = false, updatable = false)
    private ApprovalStep approvalStep;

    @Column(name = "approval_uuid")
    private UUID approvalUuid;

    @ManyToOne
    @JoinColumn(name = "approval_uuid", insertable = false, updatable = false)
    private Approval approval;

    @Column(name = "status")
    private String status;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "approved_at")
    private Date approvedAt;

}

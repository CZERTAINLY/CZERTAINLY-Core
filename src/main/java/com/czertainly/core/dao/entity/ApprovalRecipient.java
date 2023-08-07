package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.approval.ApprovalStepRecipientDto;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
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
    @Enumerated(EnumType.STRING)
    private ApprovalStatusEnum status;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "closed_at")
    private Date closedAt;

    public ApprovalStepRecipientDto mapToDto() {
        final ApprovalStepRecipientDto dto = new ApprovalStepRecipientDto();
        dto.setApprovalRecipientUuid(this.uuid.toString());
        dto.setComment(this.comment);
        dto.setStatus(this.status);
        dto.setUserUuid(this.userUuid == null ? null : this.userUuid.toString());
        dto.setCreatedAt(this.createdAt);
        dto.setClosedAt(this.closedAt);
        return dto;
    }


}

package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.approval.ApprovalDetailStepDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepDto;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.UUID;

@Data
@Entity
@Table(name = "approval_step")
public class ApprovalStep extends UniquelyIdentified {

    @Column(name = "approval_profile_version_uuid")
    private UUID approvalProfileVersionUuid;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "approval_profile_version_uuid", insertable = false, updatable = false)
    private ApprovalProfileVersion approvalProfileVersion;

    @Column(name = "user_uuid")
    private UUID userUuid;

    @Column(name = "role_uuid")
    private UUID roleUuid;

    @Column(name = "group_uuid")
    private UUID groupUuid;

    @Column(name = "description")
    private String description;

    @Column(name = "order_id")
    private int order;

    @Column(name = "required_approvals")
    private int requiredApprovals;

    public ApprovalStepDto mapToDto() {
        final ApprovalStepDto approvalStepDto = new ApprovalStepDto();
        approvalStepDto.setRequiredApprovals(this.requiredApprovals);
        approvalStepDto.setOrder(this.order);
        approvalStepDto.setUserUuid(this.userUuid);
        approvalStepDto.setRoleUuid(this.roleUuid);
        approvalStepDto.setGroupUuid(this.groupUuid);
        approvalStepDto.setDescription(this.description);
        return approvalStepDto;
    }

    public ApprovalDetailStepDto mapToDtoWithExtendedData() {
        final ApprovalStepDto approvalStepDto = mapToDto();
        final ApprovalDetailStepDto dto = new ApprovalDetailStepDto(approvalStepDto);
        dto.setApprovalStepRecipients(new ArrayList<>());
        dto.setApprovalStepUuid(this.uuid.toString());
        return dto;
    }

}

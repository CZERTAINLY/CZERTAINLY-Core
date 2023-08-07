package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDetailDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Entity
@Table(name = "approval_profile_version")
public class ApprovalProfileVersion extends UniquelyIdentifiedAndAudited {

    @ManyToOne
    @JoinColumn(name = "approval_profile_uuid", insertable = false, updatable = false)
    private ApprovalProfile approvalProfile;

    @Column(name = "approval_profile_uuid")
    private UUID approvalProfileUuid;

    @Column(name = "expiry")
    private int expiry;

    @Column(name = "description")
    private String description;

    @Column(name = "version")
    private int version = 1;

    @JsonBackReference
    @OneToMany(mappedBy = "approvalProfileVersion")
    private List<Approval> approvals;

    @OneToMany(mappedBy = "approvalProfileVersion")
    private List<ApprovalStep> approvalSteps = new ArrayList<>();


    public ApprovalProfileDto mapToDto() {
        final ApprovalProfileDto approvalProfileDto = new ApprovalProfileDto();
        approvalProfileDto.setUuid(this.getApprovalProfile().getUuid().toString());
        approvalProfileDto.setDescription(this.description);
        approvalProfileDto.setEnabled(this.getApprovalProfile().isEnabled());
        approvalProfileDto.setName(this.getApprovalProfile().getName());
        approvalProfileDto.setExpiry(this.expiry);
        approvalProfileDto.setVersion(this.version);
        approvalProfileDto.setNumberOfSteps(this.getApprovalSteps().size());
        approvalProfileDto.setAssociations(this.getApprovalProfile().getApprovalProfileRelations().size());
        return approvalProfileDto;
    }

    public ApprovalProfileDetailDto mapToDtoWithSteps() {
        final ApprovalProfileDetailDto approvalProfileDetailDto = new ApprovalProfileDetailDto();
        approvalProfileDetailDto.setUuid(this.getApprovalProfile().getUuid().toString());
        approvalProfileDetailDto.setDescription(this.description);
        approvalProfileDetailDto.setEnabled(this.getApprovalProfile().isEnabled());
        approvalProfileDetailDto.setName(this.getApprovalProfile().getName());
        approvalProfileDetailDto.setExpiry(this.expiry);
        approvalProfileDetailDto.setVersion(this.version);
        approvalProfileDetailDto.setAssociations(this.getApprovalProfile().getApprovalProfileRelations().size());

        if (approvalSteps != null) {
            approvalProfileDetailDto.setApprovalSteps(getApprovalSteps().stream().map(as -> as.mapToDto()).collect(Collectors.toList()));
        }
        return approvalProfileDetailDto;
    }

    public ApprovalProfileVersion createNewVersionObject() {
        final ApprovalProfileVersion approvalProfileVersion = new ApprovalProfileVersion();
        approvalProfileVersion.setApprovalProfile(this.approvalProfile);
        approvalProfileVersion.setApprovalProfileUuid(this.approvalProfileUuid);
        approvalProfileVersion.setVersion(this.version + 1);
        approvalProfileVersion.setExpiry(this.expiry);
        approvalProfileVersion.setDescription(this.description);
        approvalProfileVersion.setApprovalSteps(new ArrayList<>());
        return approvalProfileVersion;
    }

}

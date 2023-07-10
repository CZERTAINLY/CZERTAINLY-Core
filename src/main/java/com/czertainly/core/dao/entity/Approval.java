package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.enums.ApprovalStatusEnum;
import com.czertainly.core.model.auth.ResourceAction;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "approval")
@EntityListeners(AuditingEntityListener.class)
public class Approval extends UniquelyIdentified {

    @Column(name = "approval_profile_version_uuid")
    private UUID approvalProfileVersionUuid;

    @ManyToOne
    @JoinColumn(name = "approval_profile_version_uuid", insertable = false, updatable = false)
    private ApprovalProfileVersion approvalProfileVersion;

    @Column(name = "creator_uuid")
    private UUID creatorUuid;

    @Column(name = "object_uuid")
    private UUID objectUuid;

    @Column(name = "resource")
    @Enumerated(EnumType.STRING)
    private Resource resource;

    @Column(name = "action")
    @Enumerated(EnumType.STRING)
    private ResourceAction action;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ApprovalStatusEnum status;

    @CreatedDate
    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "closed_at")
    private Date closedAt;

    @OneToMany(mappedBy = "approval")
    private List<ApprovalRecipient> approvalRecipients;

}

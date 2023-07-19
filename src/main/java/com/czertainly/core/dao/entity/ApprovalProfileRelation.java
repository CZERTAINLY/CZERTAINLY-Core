package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "approval_profile_relation")
public class ApprovalProfileRelation extends UniquelyIdentified {

    @ManyToOne
    @JoinColumn(name = "approval_profile_uuid", insertable = false, updatable = false)
    private ApprovalProfile approvalProfile;

    @Column(name = "approval_profile_uuid")
    private UUID approvalProfileUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource")
    private Resource resource;

    @Column(name = "resource_uuid")
    private UUID resourceUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private ResourceAction action;


}

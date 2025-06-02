package com.czertainly.core.events.data;

import com.czertainly.api.model.client.approvalprofile.ApprovalStepDto;
import com.czertainly.api.model.common.events.data.ApprovalEventData;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfile;

public class EventDataBuilder {

    private EventDataBuilder() {
    }

    public static ApprovalEventData getApprovalEventData(Approval approval, ApprovalProfile approvalProfile, String creatorUsername) {
        ApprovalEventData eventData = new ApprovalEventData();
        eventData.setApprovalUuid(approval.getUuid());
        eventData.setApprovalProfileUuid(approvalProfile.getUuid());
        eventData.setApprovalProfileName(approvalProfile.getName());
        eventData.setVersion(approval.getApprovalProfileVersion().getVersion());
        eventData.setStatus(approval.getStatus());
        eventData.setExpiryAt(approval.getExpiryAt());
        eventData.setClosedAt(approval.getClosedAt());
        eventData.setResource(approval.getResource());
        eventData.setResourceAction(approval.getAction().getCode());
        eventData.setObjectUuid(approval.getObjectUuid());
        eventData.setCreatorUuid(approval.getCreatorUuid());
        eventData.setCreatorUsername(creatorUsername);

        return eventData;
    }

    public static ApprovalEventData getApprovalRequestedEventData(Approval approval, ApprovalProfile approvalProfile, ApprovalStepDto approvalStepDto, String creatorUsername) {
        ApprovalEventData eventData = getApprovalEventData(approval, approvalProfile, creatorUsername);

        if (approvalStepDto.getUserUuid() != null) {
            eventData.setRecipientType(RecipientType.USER);
            eventData.setRecipientUuid(approvalStepDto.getUserUuid());
        } else if (approvalStepDto.getRoleUuid() != null) {
            eventData.setRecipientType(RecipientType.ROLE);
            eventData.setRecipientUuid(approvalStepDto.getRoleUuid());
        } else if (approvalStepDto.getGroupUuid() != null) {
            eventData.setRecipientType(RecipientType.GROUP);
            eventData.setRecipientUuid(approvalStepDto.getGroupUuid());
        }

        return eventData;
    }
}
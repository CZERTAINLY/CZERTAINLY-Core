package com.czertainly.core.events.handlers;

import com.czertainly.api.model.client.approval.ApprovalDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepDto;
import com.czertainly.api.model.common.events.data.ApprovalEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.dao.entity.ApprovalStep;
import com.czertainly.core.dao.repository.ApprovalRepository;
import com.czertainly.core.evaluator.TriggerEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.events.transaction.UpdateCertificateHistoryEvent;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.APPROVAL_REQUESTED)
public class ApprovalRequestedEventHandler extends EventHandler<Approval> {

    private AuthHelper authHelper;

    @Autowired
    public void setAuthHelper(AuthHelper authHelper) {
        this.authHelper = authHelper;
    }

    @Autowired
    protected ApprovalRequestedEventHandler(ApprovalRepository repository, TriggerEvaluator<Approval> ruleEvaluator) {
        super(repository, ruleEvaluator);
    }

    @Override
    protected Object getEventData(Approval approval, Object eventMessageData) {
        ApprovalProfile approvalProfile = approval.getApprovalProfileVersion().getApprovalProfile();

        ApprovalEventData eventData = new ApprovalEventData();
        eventData.setApprovalUuid(approval.getUuid().toString());
        eventData.setApprovalProfileUuid(approvalProfile.getUuid().toString());
        eventData.setApprovalProfileName(approvalProfile.getName());
        eventData.setVersion(approval.getApprovalProfileVersion().getVersion());
        eventData.setStatus(approval.getStatus());
        eventData.setExpiryAt(approval.getExpiryAt());
        eventData.setClosedAt(approval.getClosedAt());
        eventData.setResource(approval.getResource());
        eventData.setResourceAction(approval.getAction().getCode());
        eventData.setObjectUuid(approval.getObjectUuid().toString());
        eventData.setCreatorUuid(approval.getCreatorUuid().toString());
        eventData.setCreatorUsername(authHelper.getUserUsername(eventData.getCreatorUuid()));

        return eventData;
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<Approval> eventContext) {
        Approval approval = eventContext.getResourceObjects().getFirst();
        ApprovalEventData eventData = (ApprovalEventData) eventContext.getResourceObjectsEventData().getFirst();
        ApprovalStepDto approvalStepDto = objectMapper.convertValue(eventContext.getData(), ApprovalStepDto.class);


        List<NotificationRecipient> recipients;
        if (approvalStepDto.getUserUuid() != null) {
            recipients = NotificationRecipient.buildUserNotificationRecipient(approvalStepDto.getUserUuid());
        } else if (approvalStepDto.getRoleUuid() != null) {
            recipients = NotificationRecipient.buildRoleNotificationRecipient(approvalStepDto.getRoleUuid());
        } else if (approvalStepDto.getGroupUuid() != null) {
            recipients = NotificationRecipient.buildGroupNotificationRecipient(approvalStepDto.getGroupUuid());
        } else {
            recipients = new ArrayList<>();
        }
        NotificationMessage notificationMessage = new NotificationMessage(eventContext.getResourceEvent(), Resource.APPROVAL, approval.getUuid(), null, recipients, eventData);
        notificationProducer.produceMessage(notificationMessage);

        // produce only for certificates for now until refactoring and uniting of event history for all resources
        if (approval.getResource() == Resource.CERTIFICATE) {
            ApprovalStep firstApprovalStep = approval.getApprovalProfileVersion().getApprovalSteps().stream().min(Comparator.comparing(ApprovalStep::getOrder)).orElse(null);
            // add history record only for approval request for first step
            if (firstApprovalStep != null && firstApprovalStep.getUuid().equals(approvalStepDto.getUuid())) {
                applicationEventPublisher.publishEvent(new UpdateCertificateHistoryEvent(approval.getObjectUuid(), CertificateEvent.APPROVAL_REQUEST, CertificateEventStatus.SUCCESS, "Approval requested for action %s with approval profile %s".formatted(approval.getAction().getCode(), approval.getApprovalProfileVersion().getApprovalProfile().getName()), null));
            }
        }
    }

    public static EventMessage constructEventMessage(UUID approvalUuid, ApprovalStepDto approvalStepDto) {
        return new EventMessage(ResourceEvent.APPROVAL_REQUESTED, Resource.APPROVAL, approvalUuid, approvalStepDto);
    }
}

package com.czertainly.core.events.handlers;

import com.czertainly.api.model.client.approval.ApprovalDto;
import com.czertainly.api.model.common.events.data.ApprovalEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfile;
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

import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.APPROVAL_CLOSED)
public class ApprovalClosedEventHandler extends EventHandler<Approval> {

    private AuthHelper authHelper;

    @Autowired
    public void setAuthHelper(AuthHelper authHelper) {
        this.authHelper = authHelper;
    }

    @Autowired
    protected ApprovalClosedEventHandler(ApprovalRepository repository, TriggerEvaluator<Approval> ruleEvaluator) {
        super(repository, ruleEvaluator);
    }

    @Override
    protected Object getEventData(Approval approval, Object eventMessageData) {
        ApprovalProfile approvalProfile = approval.getApprovalProfileVersion().getApprovalProfile();

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
        eventData.setCreatorUsername(authHelper.getUserUsername(eventData.getCreatorUuid().toString()));

        return eventData;
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<Approval> eventContext) {
        Approval approval = eventContext.getResourceObjects().getFirst();
        ApprovalEventData eventData = (ApprovalEventData) eventContext.getResourceObjectsEventData().getFirst();

        NotificationMessage notificationMessage = new NotificationMessage(eventContext.getResourceEvent(), Resource.APPROVAL, approval.getUuid(), null, NotificationRecipient.buildUserNotificationRecipient(approval.getCreatorUuid()), eventData);
        notificationProducer.produceMessage(notificationMessage);

        // produce only for certificates for now until refactoring and uniting of event history for all resources
        if (approval.getResource() == Resource.CERTIFICATE) {
            applicationEventPublisher.publishEvent(new UpdateCertificateHistoryEvent(approval.getObjectUuid(), CertificateEvent.APPROVAL_CLOSE, CertificateEventStatus.SUCCESS, "Approval for action %s with approval profile %s closed with status %s".formatted(approval.getAction().getCode(), eventData.getApprovalProfileName(), eventData.getStatus().getLabel()), null));
        }
    }

    public static EventMessage constructEventMessage(UUID approvalUuid) {
        return new EventMessage(ResourceEvent.APPROVAL_CLOSED, Resource.APPROVAL, approvalUuid, null);
    }
}

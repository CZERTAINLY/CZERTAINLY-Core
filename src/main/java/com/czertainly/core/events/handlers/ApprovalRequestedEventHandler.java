package com.czertainly.core.events.handlers;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.repository.ApprovalRepository;
import com.czertainly.core.evaluator.RuleEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.security.authz.SecuredUUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.APPROVAL_REQUESTED)
public class ApprovalRequestedEventHandler extends EventHandler<Approval> {

    private RuleEvaluator<Approval> ruleEvaluator;
    private ApprovalRepository approvalRepository;

    @Autowired
    public void setRuleEvaluator(RuleEvaluator<Approval> ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    @Autowired
    public void setApprovalRepository(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @Override
    protected EventContext<Approval> prepareContext(EventMessage eventMessage) throws EventException {
        Approval approval = approvalRepository.findByUuid(SecuredUUID.fromUUID(eventMessage.getObjectUuid())).orElseThrow(() -> new EventException(eventMessage.getResourceEvent(), "Approval with UUID %s not found".formatted(eventMessage.getObjectUuid())));

        // TODO: load triggers from platform
        return new EventContext<>(eventMessage, ruleEvaluator, approval);
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<Approval> eventContext) {
        Approval approval = eventContext.getResourceObjects().getFirst();
        ApprovalStepDto approvalStepDto = (ApprovalStepDto) eventContext.getData();
        notificationProducer.produceNotificationApprovalRequested(eventContext.getResource(), approval.getUuid(), approval.mapToDto(), approvalStepDto, approval.getCreatorUuid().toString());
    }

    public static EventMessage constructEventMessage(UUID approvalUuid, ApprovalStepDto approvalStepDto) {
        return new EventMessage(ResourceEvent.APPROVAL_REQUESTED, Resource.APPROVAL, approvalUuid, approvalStepDto);
    }
}

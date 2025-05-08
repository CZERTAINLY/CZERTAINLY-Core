package com.czertainly.core.events.handlers;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.evaluator.CertificateRuleEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.model.auth.ResourceAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.CERTIFICATE_ACTION_PERFORMED)
public class CertificateActionPerformedEventHandler extends EventHandler<Certificate> {

    protected CertificateActionPerformedEventHandler(CertificateRepository repository, CertificateRuleEvaluator ruleEvaluator) {
        super(repository, ruleEvaluator);
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<Certificate> eventContext) {
        // TODO: move event history logging to event handler? And should it also trigger action performed failed as event?
        Certificate certificate = eventContext.getResourceObjects().getFirst();
        ResourceAction action = objectMapper.convertValue(eventContext.getData(), ResourceAction.class);
        notificationProducer.produceNotificationCertificateActionPerformed(certificate.mapToListDto(), action, null);
    }

    public static EventMessage constructEventMessage(UUID certificateUuid, ResourceAction action) {
        return new EventMessage(ResourceEvent.CERTIFICATE_ACTION_PERFORMED, Resource.CERTIFICATE, certificateUuid, action);
    }

}

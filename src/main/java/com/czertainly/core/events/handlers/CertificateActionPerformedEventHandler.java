package com.czertainly.core.events.handlers;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.evaluator.CertificateRuleEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.model.auth.ResourceAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.CERTIFICATE_ACTION_PERFORMED)
public class CertificateActionPerformedEventHandler extends EventHandler<Certificate> {

    private CertificateRuleEvaluator ruleEvaluator;
    private CertificateRepository certificateRepository;

    @Autowired
    public void setRuleEvaluator(CertificateRuleEvaluator ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Override
    protected EventContext<Certificate> prepareContext(EventMessage eventMessage) throws EventException {
        Certificate certificate = certificateRepository.findWithAssociationsByUuid(eventMessage.getObjectUuid()).orElseThrow(() -> new EventException(eventMessage.getResourceEvent(), "Certificate with UUID %s not found".formatted(eventMessage.getObjectUuid())));

        // TODO: load triggers from platform
        return new EventContext<>(eventMessage, ruleEvaluator, certificate);
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<Certificate> eventContext) {
        // TODO: move event history logging to event handler? And should it also trigger action performed failed as event?
        Certificate certificate = eventContext.getResourceObjects().getFirst();
        ResourceAction action = (ResourceAction) eventContext.getData();
        notificationProducer.produceNotificationCertificateActionPerformed(certificate.mapToListDto(), action, null);
    }

    public static EventMessage constructEventMessage(UUID certificateUuid, ResourceAction action) {
        return new EventMessage(ResourceEvent.CERTIFICATE_ACTION_PERFORMED, Resource.CERTIFICATE, certificateUuid, action);
    }

}

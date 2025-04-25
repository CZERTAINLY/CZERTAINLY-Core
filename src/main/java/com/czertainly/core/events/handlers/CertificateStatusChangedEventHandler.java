package com.czertainly.core.events.handlers;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.evaluator.CertificateRuleEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.events.transaction.UpdateCertificateHistoryEvent;
import com.czertainly.core.messaging.model.EventMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public class CertificateStatusChangedEventHandler extends EventHandler<Certificate> {

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
        Certificate certificate = eventContext.getResourceObjects().getFirst();
        CertificateValidationStatus[] statusArrayData = (CertificateValidationStatus[]) eventContext.getData();
        notificationProducer.produceNotificationCertificateStatusChanged(statusArrayData[0], statusArrayData[1], certificate.mapToListDto());

        // handle certificate event history record
        applicationEventPublisher.publishEvent(new UpdateCertificateHistoryEvent(certificate.getUuid(), CertificateEvent.UPDATE_VALIDATION_STATUS, CertificateEventStatus.SUCCESS, statusArrayData[0], statusArrayData[1]));
    }

    public static EventMessage constructEventMessage(UUID certificateUuid, CertificateValidationStatus oldStatus, CertificateValidationStatus newStatus) {
        CertificateValidationStatus[] statusArrayData = new CertificateValidationStatus[] { oldStatus, newStatus };
        return new EventMessage(ResourceEvent.CERTIFICATE_STATUS_CHANGED, Resource.CERTIFICATE, certificateUuid, statusArrayData);
    }

}

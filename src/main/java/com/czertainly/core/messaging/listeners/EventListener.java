package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.DiscoveryService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Component
@Transactional
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    private CertificateEventHistoryService certificateEventHistoryService;
    private DiscoveryService discoveryService;

    @Autowired
    public void setDiscoveryService(DiscoveryService discoveryService){
        this.discoveryService = discoveryService;
    }

    @Autowired
    public void setCertificateEventHistoryService(CertificateEventHistoryService certificateEventHistoryService) {
        this.certificateEventHistoryService = certificateEventHistoryService;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_EVENTS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(EventMessage eventMessage) throws NotFoundException, CertificateException, NoSuchAlgorithmException, RuleException, AttributeException {
        switch (eventMessage.getResource()) {
            case CERTIFICATE -> certificateEventHistoryService.addEventHistory(eventMessage.getResourceUUID(), CertificateEvent.findByCode(eventMessage.getEventName()), CertificateEventStatus.valueOf(eventMessage.getEventStatus()), eventMessage.getEventMessage(), eventMessage.getEventDetail());
            case DISCOVERY -> discoveryService.evaluateDiscoveryTriggers(eventMessage.getResourceUUID());
            default -> logger.warn("Event handling is supported only for certificates for now");
        }
    }

}

package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.core.util.AuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Objects;

@Component
@Transactional
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    private CertificateEventHistoryService certificateEventHistoryService;
    private DiscoveryService discoveryService;
    private AuthHelper authHelper;

    @Autowired
    public void setAuthHelper(AuthHelper authHelper) {
        this.authHelper = authHelper;
    }
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
            case DISCOVERY ->
            {
                authHelper.authenticateAsUser(eventMessage.getUserUuid());
                if (Objects.equals(eventMessage.getEventName(), ResourceEvent.DISCOVERY_FINISHED.getCode())) discoveryService.evaluateDiscoveryTriggers(eventMessage.getResourceUUID(), eventMessage.getUserUuid());
            }
            default -> logger.warn("Event handling is supported only for certificates for now");
        }
    }

}

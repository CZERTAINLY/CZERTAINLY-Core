package com.czertainly.core.messaging.producers;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventProducer {

    private static final Logger logger = LoggerFactory.getLogger(EventProducer.class);
    private RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(final RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void produceMessage(final EventMessage eventMessage) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.EVENT_ROUTING_KEY, eventMessage);
    }

    public void produceCertificateEventMessage(final UUID certificateUUID, final String eventName, final String eventStatus, final String message, final String detail) {
        logger.debug("Sending Certificate {} event message: {}", certificateUUID, message);
        final EventMessage eventMessage = new EventMessage(Resource.CERTIFICATE, certificateUUID, eventName, eventStatus, message, detail);
        produceMessage(eventMessage);
    }

    public void produceCertificateStatusChangeEventMessage(final UUID certificateUUID, final String eventName, final String eventStatus, CertificateStatus oldStatus, CertificateStatus newStatus) {
        String message = String.format("Certificate status changed from %s to %s.", oldStatus.getLabel(), newStatus.getLabel());
        logger.debug("Sending Certificate {} event message: {}", certificateUUID, message);
        final EventMessage eventMessage = new EventMessage(Resource.CERTIFICATE, certificateUUID, eventName, eventStatus, message, null);
        produceMessage(eventMessage);
    }

}

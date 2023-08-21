package com.czertainly.core.messaging.producers;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventProducer {

    private RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(final RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void produceMessage(final EventMessage eventMessage) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.EVENT_ROUTING_KEY, eventMessage);
    }

    public void produceEventCertificateMessage(final UUID certificateUUID, final String eventName, final String eventStatus, final String message, final String detail) {
        final EventMessage eventMessage = new EventMessage(Resource.CERTIFICATE, certificateUUID, eventName, eventStatus, message, detail);
        produceMessage(eventMessage);
    }

}

package com.czertainly.core.messaging.producers;

import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.model.enums.ResourceTypeEnum;
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

    public void produceEventCertificateMessage(final UUID certificateUUID, final String name) {
        final EventMessage eventMessage = new EventMessage(ResourceTypeEnum.CERTIFICATE, certificateUUID, name);
        produceMessage(eventMessage);
    }

}

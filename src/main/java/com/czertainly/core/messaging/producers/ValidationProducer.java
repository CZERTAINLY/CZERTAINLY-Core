package com.czertainly.core.messaging.producers;

import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.ValidationMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ValidationProducer {

    private static final String TTL_IN_MS = String.valueOf(24 * 60 * 60 * 1000); // 24 hours

    private RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(final RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void produceMessage(final ValidationMessage validationMessage) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.VALIDATION_ROUTING_KEY, validationMessage, msg -> {
            msg.getMessageProperties().setExpiration(TTL_IN_MS);
            return msg;
        });
    }

}

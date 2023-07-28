package com.czertainly.core.messaging.producers;

import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.ActionMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActionProducer {

    private RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(final RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void produceMessage(final ActionMessage actionMessage) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.ACTION_ROUTING_KEY, actionMessage);
    }

}

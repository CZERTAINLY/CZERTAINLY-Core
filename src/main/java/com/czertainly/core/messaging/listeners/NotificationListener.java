package com.czertainly.core.messaging.listeners;

import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);

    @RabbitListener(queues = RabbitMQConstants.QUEUE_NOTIFICATIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(EventMessage eventMessage) {
        logger.info("Received notification message: {}", eventMessage);

        // TODO process the message

    }

}

package com.czertainly.core.messaging.listeners;

import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.producers.NotificationProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    @Autowired
    private NotificationProducer notificationProducer;

    @RabbitListener(queues = "core.events", messageConverter = "jsonMessageConverter")
    public void processMessage(EventMessage eventMessage) {
        logger.info("Received event message: {}", eventMessage);

        // TODO process the message

        final NotificationMessage notificationMessage = new NotificationMessage(eventMessage);

        // TODO add another important data to notification message

        notificationProducer.produceMessage(notificationMessage);

    }

}

package com.czertainly.core.messaging.producers;

import com.czertainly.api.model.common.events.data.InternalNotificationEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class NotificationProducer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProducer.class);

    private RabbitTemplate rabbitTemplate;

    @Autowired
    public void setRabbitTemplate(final RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void produceMessage(final NotificationMessage notificationMessage) {
        if ((notificationMessage.getNotificationProfileUuids() == null || notificationMessage.getNotificationProfileUuids().isEmpty()) && (notificationMessage.getRecipients() == null || notificationMessage.getRecipients().isEmpty())) {
            logger.warn("Recipients for notification of event {} is empty. Message: {}", notificationMessage.getEvent().getLabel(), notificationMessage);
        } else {
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.NOTIFICATION_ROUTING_KEY, notificationMessage);
        }
    }

    public void produceInternalNotificationMessage(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, String text, String detail) {
        produceMessage(new NotificationMessage(null,
                resource,
                resourceUUID,
                null,
                recipients,
                new InternalNotificationEventData(text, detail)));
    }
}

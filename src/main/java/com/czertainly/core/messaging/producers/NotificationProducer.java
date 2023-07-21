package com.czertainly.core.messaging.producers;

import com.czertainly.api.model.connector.notification.*;
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

    protected void produceMessage(final NotificationMessage notificationMessage) {
        if (notificationMessage.getRecipients() == null) {
            logger.error("Recipients for notification {} can't be empty.", notificationMessage.getType());
        } else {
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.NOTIFICATION_ROUTING_KEY, notificationMessage);
        }
    }

    public void produceNotification(NotificationType type, Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, Object data) {
        produceMessage(new NotificationMessage(type, resource, resourceUUID, recipients, data));
    }

    public void produceNotificationStatusChange(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, String oldStatus, String newStatus) {
        produceMessage(new NotificationMessage(NotificationType.STATUS_CHANGE,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataStatusChange(oldStatus, newStatus)));
    }

    public void produceNotificationScheduledJobCompleted(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, String jobName, String status) {
        produceMessage(new NotificationMessage(NotificationType.SCHEDULED_JOB_COMPLETED,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataScheduledJobCompleted(jobName, status)));
    }

    public void produceNotificationText(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, String text) {
        produceMessage(new NotificationMessage(NotificationType.TEXT,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataText(text)));
    }

    public void produceNotificationNewApprovalStep(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients) {
        produceMessage(new NotificationMessage(NotificationType.APPROVAL_NEW_STEP,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataApprovalNewStep()));
    }

    public void produceNotificationNewApproval(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients) {
        produceMessage(new NotificationMessage(NotificationType.NEW_APPROVAL,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataApprovalNewStep()));
    }

    public void produceNotificationCloseApproval(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients) {
        produceMessage(new NotificationMessage(NotificationType.CLOSE_APPROVAL,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataApprovalNewStep()));
    }

}

package com.czertainly.core.messaging.listeners;

import com.czertainly.api.model.connector.notification.NotificationDataScheduledJobCompleted;
import com.czertainly.api.model.connector.notification.NotificationDataStatusChange;
import com.czertainly.api.model.connector.notification.NotificationDataText;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.core.enums.RecipientTypeEnum;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);

    @Autowired
    NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_NOTIFICATIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(NotificationMessage notificationMessage) {
        logger.info("Received notification message: {}", notificationMessage);

        if (notificationMessage.getData() == null || notificationMessage.getType()
                .getNotificationData()
                .isInstance(notificationMessage.getData())) {

            if (notificationMessage.getData() != null) {
                if (notificationMessage.getType().equals(NotificationType.STATUS_CHANGE)) {
                    NotificationDataStatusChange data = (NotificationDataStatusChange) notificationMessage.getData();
                    sendInternalNotifications(notificationMessage.getResource().getLabel() + " status changed.",
                            "Previous status: " + data.getOldStatus() + ", new status: " + data.getNewStatus(),
                            notificationMessage);
                }

                if (notificationMessage.getType().equals(NotificationType.SCHEDULED_JOB_COMPLETED)) {
                    NotificationDataScheduledJobCompleted data = (NotificationDataScheduledJobCompleted) notificationMessage.getData();
                    sendInternalNotifications("Scheduled job " + data.getJobName() + " finished with status: " + data.getStatus() + ".",
                            null,
                            notificationMessage);
                }

                if (notificationMessage.getType().equals(NotificationType.TEXT)) {
                    NotificationDataText data = (NotificationDataText) notificationMessage.getData();
                    sendInternalNotifications(data.getText(), null, notificationMessage);
                }
            }
        }
    }

    private void sendInternalNotifications(String message, String detail, NotificationMessage notificationMessage) {
        for (NotificationRecipient recipient : notificationMessage.getRecipients()) {
            if (recipient.getRecipientType().equals(RecipientTypeEnum.USER)) {
                notificationService.createNotificationForUser(message,
                        detail,
                        recipient.getRecipientUuid().toString(),
                        notificationMessage.getResource(),
                        notificationMessage.getResourceUUID() != null ? notificationMessage.getResourceUUID().toString() : null);
            }
            if (recipient.getRecipientType().equals(RecipientTypeEnum.ROLE)) {
                notificationService.createNotificationForRole(message,
                        detail,
                        recipient.getRecipientUuid().toString(),
                        notificationMessage.getResource(),
                        notificationMessage.getResourceUUID() != null ? notificationMessage.getResourceUUID().toString() : null);
            }
            if (recipient.getRecipientType().equals(RecipientTypeEnum.GROUP)) {
                notificationService.createNotificationForGroup(message,
                        detail,
                        recipient.getRecipientUuid().toString(),
                        notificationMessage.getResource(),
                        notificationMessage.getResourceUUID() != null ? notificationMessage.getResourceUUID().toString() : null);
            }
        }
    }

}

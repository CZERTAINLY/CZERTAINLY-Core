package com.czertainly.core.messaging.listeners;

import com.czertainly.api.model.client.approval.ApprovalDto;
import com.czertainly.api.model.connector.notification.data.NotificationDataApproval;
import com.czertainly.api.model.connector.notification.data.NotificationDataScheduledJobCompleted;
import com.czertainly.api.model.connector.notification.data.NotificationDataCertificateStatusChanged;
import com.czertainly.api.model.connector.notification.data.NotificationDataText;
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
                switch (notificationMessage.getType()) {
                    case TEXT -> {
                        NotificationDataText data = (NotificationDataText) notificationMessage.getData();
                        sendInternalNotifications(data.getText(), data.getDetail(), notificationMessage);
                    }
                    case CERTIFICATE_STATUS_CHANGED -> {
                        NotificationDataCertificateStatusChanged data = (NotificationDataCertificateStatusChanged) notificationMessage.getData();
                        sendInternalNotifications(
                                String.format("Certificate status changed from %s to %s for certificate identified as `%s` with serial number `%s` issued by `%s`",
                                        data.getOldStatus(), data.getNewStatus(), data.getSubjectDn(), data.getSerialNumber(), data.getIssuerDn()),
                                null,
                                notificationMessage);
                    }
                    case SCHEDULED_JOB_COMPLETED -> {
                        NotificationDataScheduledJobCompleted data = (NotificationDataScheduledJobCompleted) notificationMessage.getData();
                        sendInternalNotifications(
                                String.format("%s scheduled task has finished for %s with result %s",
                                        data.getJobType(), data.getJobName(), data.getStatus()),
                                null,
                                notificationMessage);
                    }
                    case APPROVAL_REQUESTED -> {
                        NotificationDataApproval data = (NotificationDataApproval) notificationMessage.getData();
                        sendInternalNotifications(
                                String.format("Request %s for %s from %s is waiting to be approved until %s",
                                        data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getExpiryAt()),
                                getApprovalNotificationDetail(data),
                                notificationMessage);
                    }
                    case APPROVAL_CLOSED -> {
                        NotificationDataApproval data = (NotificationDataApproval) notificationMessage.getData();
                        sendInternalNotifications(
                                String.format("Request %s for %s from %s is %s",
                                        data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getStatus().getLabel()),
                                getApprovalNotificationDetail(data),
                                notificationMessage);
                    }
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

    private String getApprovalNotificationDetail(NotificationDataApproval approvalData) {
        return String.format("Approval profile name: %s,\nResource: %s,\nResource action: %s,\nObject UUID: %s",
                approvalData.getApprovalProfileName(), approvalData.getResource().getLabel(), approvalData.getResourceAction(), approvalData.getObjectUuid());
    }

}

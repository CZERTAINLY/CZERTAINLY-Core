package com.czertainly.core.messaging.listeners;

import com.czertainly.api.clients.NotificationInstanceApiClient;
import com.czertainly.api.exception.ConnectionServiceException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.connector.NotificationInstanceController;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.api.model.connector.notification.NotificationRecipientDto;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.connector.notification.data.NotificationDataApproval;
import com.czertainly.api.model.connector.notification.data.NotificationDataScheduledJobCompleted;
import com.czertainly.api.model.connector.notification.data.NotificationDataCertificateStatusChanged;
import com.czertainly.api.model.connector.notification.data.NotificationDataText;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.settings.NotificationSettingsDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.Notification;
import com.czertainly.core.dao.entity.NotificationInstanceReference;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.NotificationInstanceReferenceRepository;
import com.czertainly.core.enums.RecipientTypeEnum;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.NotificationService;
import com.czertainly.core.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);

    @Autowired
    NotificationService notificationService;

    @Autowired
    SettingService settingService;

    @Autowired
    NotificationInstanceApiClient notificationInstanceApiClient;

    NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;

    @Autowired
    UserManagementApiClient userManagementApiClient;

    @Autowired
    RoleManagementApiClient roleManagementApiClient;

    GroupRepository groupRepository;



    @RabbitListener(queues = RabbitMQConstants.QUEUE_NOTIFICATIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(NotificationMessage notificationMessage) throws ConnectorException {
        logger.info("Received notification message: {}", notificationMessage);

//        dont forget to solve what to do if there is no setting
        NotificationSettingsDto notificationSettingsDto = settingService.getNotificationSettings();
        Map<NotificationType, String> notificationTypeStringMap = notificationSettingsDto.getNotificationsMapping();

        if (notificationMessage.getData() == null || notificationMessage.getType()
                .getNotificationData()
                .isInstance(notificationMessage.getData())) {

            if (notificationMessage.getData() != null) {
                switch (notificationMessage.getType()) {
                    case TEXT -> {
                        NotificationDataText data = (NotificationDataText) notificationMessage.getData();
                        sendInternalNotifications(data.getText(), data.getDetail(), notificationMessage);
                        String notificationInstanceUUID = notificationTypeStringMap.get(NotificationType.TEXT);
                        if (notificationInstanceUUID != null) {
                            sendExternalNotifications(notificationInstanceUUID, notificationMessage, NotificationType.TEXT);
                        }
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


    private void sendExternalNotifications(String notificationInstanceUUID, NotificationMessage notificationMessage, NotificationType notificationType) throws ConnectorException {
        Optional<NotificationInstanceReference> notificationInstanceReference = notificationInstanceReferenceRepository.findByUuid(UUID.fromString(notificationInstanceUUID));
        ConnectorDto connector = notificationInstanceReference.get().getConnector().mapToDto();

        List<NotificationRecipientDto> recipientsDto = new ArrayList<>();
        for (NotificationRecipient recipient : notificationMessage.getRecipients()) {
            if (recipient.getRecipientType().equals(RecipientTypeEnum.USER)) {
                UUID recipientUuid = recipient.getRecipientUuid();
                try {
                    UserDetailDto userDetailDto = userManagementApiClient.getUserDetail(recipientUuid.toString());
                    NotificationRecipientDto user = new NotificationRecipientDto();
                    user.setEmail(userDetailDto.getEmail());
                    user.setName(userDetailDto.getUsername());
                    recipientsDto.add(user);
                } catch (Exception e) {
                    logger.warn(String.format("User with UUID %s was not reached, notification was not sent for this user."));
                }
            }
            if (recipient.getRecipientType().equals(RecipientTypeEnum.ROLE)) {
                UUID roleUuid = recipient.getRecipientUuid();
                List<UserDto> userDtos = roleManagementApiClient.getRoleUsers(roleUuid.toString());
                for (UserDto userDto : userDtos) {
                    NotificationRecipientDto user = new NotificationRecipientDto();
                    user.setEmail(userDto.getEmail());
                    user.setName(userDto.getUsername());
                    recipientsDto.add(user);
                }
            }
            if (recipient.getRecipientType().equals(RecipientTypeEnum.GROUP)) {
                UUID groupUuid = recipient.getRecipientUuid();
                Optional<Group> group = groupRepository.findByUuid(groupUuid);
                NotificationRecipientDto groupDto = new NotificationRecipientDto();
                groupDto.setName(group.get().getName());
                groupDto.setEmail(group.get().getEmail());
                recipientsDto.add(groupDto);
            }
        }

        NotificationProviderNotifyRequestDto notificationProviderNotifyRequestDto = new NotificationProviderNotifyRequestDto();
        notificationProviderNotifyRequestDto.setNotificationData(notificationMessage.getData());
        notificationProviderNotifyRequestDto.setResource(notificationMessage.getResource());
        notificationProviderNotifyRequestDto.setEventType(notificationType);
        notificationProviderNotifyRequestDto.setRecipients(recipientsDto);

        notificationInstanceApiClient.sendNotification(connector, notificationInstanceReference.get().getNotificationInstanceUuid().toString(), notificationProviderNotifyRequestDto);

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

package com.czertainly.core.messaging.listeners;

import com.czertainly.api.clients.NotificationInstanceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.api.model.connector.notification.NotificationRecipientDto;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.connector.notification.data.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.RoleDetailDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.settings.NotificationSettingsDto;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.NotificationInstanceMappedAttributes;
import com.czertainly.core.dao.entity.NotificationInstanceReference;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.NotificationInstanceReferenceRepository;
import com.czertainly.core.enums.RecipientTypeEnum;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.NotificationService;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Transactional
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);

    private ObjectMapper mapper = new ObjectMapper();

    private NotificationService notificationService;

    private SettingService settingService;

    private AttributeService attributeService;

    private NotificationInstanceApiClient notificationInstanceApiClient;

    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;

    private UserManagementApiClient userManagementApiClient;

    private RoleManagementApiClient roleManagementApiClient;

    private GroupRepository groupRepository;

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired
    public void setSettingService(SettingService settingService) {
        this.settingService = settingService;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Autowired
    public void setNotificationInstanceApiClient(NotificationInstanceApiClient notificationInstanceApiClient) {
        this.notificationInstanceApiClient = notificationInstanceApiClient;
    }

    @Autowired
    public void setNotificationInstanceReferenceRepository(NotificationInstanceReferenceRepository notificationInstanceReferenceRepository) {
        this.notificationInstanceReferenceRepository = notificationInstanceReferenceRepository;
    }

    @Autowired
    public void setUserManagementApiClient(UserManagementApiClient userManagementApiClient) {
        this.userManagementApiClient = userManagementApiClient;
    }

    @Autowired
    public void setRoleManagementApiClient(RoleManagementApiClient roleManagementApiClient) {
        this.roleManagementApiClient = roleManagementApiClient;
    }

    @Autowired
    public void setGroupRepository(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_NOTIFICATIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(NotificationMessage notificationMessage) {
        logger.debug("Received notification message: {}", notificationMessage);

        if (notificationMessage.getData() == null) {
            // TODO: convert it with ObjectMapper before to check if it is correct type
            logger.warn("Missing notification data or has incompatible data type. Message: " + notificationMessage);
            return;
        }

        // check settings and send external notifications
        String notificationInstanceUUID;
        NotificationSettingsDto notificationSettingsDto = settingService.getNotificationSettings();
        Map<NotificationType, String> notificationTypeStringMap = notificationSettingsDto.getNotificationsMapping();
        if (notificationTypeStringMap != null && (notificationInstanceUUID = notificationTypeStringMap.get(notificationMessage.getType())) != null) {
            logger.debug("Sending notification message externally. Notification instance UUID: {}", notificationInstanceUUID);
            try {
                sendExternalNotifications(UUID.fromString(notificationInstanceUUID), notificationMessage);
                logger.debug("Sending notification message externally successful.");
            } catch (NotFoundException e) {
                logger.warn("Notification instance {} configured for notification type {} was not found.", notificationInstanceUUID, notificationMessage.getType());
            } catch (ConnectorException e) {
                logger.warn("Error in sending request to connector of notification instance {} configured for notification type {}: {}", notificationInstanceUUID, notificationMessage.getType(), e);
            } catch (ValidationException e) {
                logger.warn("Validation error in sending notification to connector of notification instance {} configured for notification type {}: {}", notificationInstanceUUID, notificationMessage.getType(), e.getMessage());
            } catch (Exception e) {
                logger.error("Error in external notification with notification instance {}: {}", notificationInstanceUUID, e.toString());
            }
        }

        // send internal notifications
        try {
            sendInternalNotifications(notificationMessage);
        } catch (ValidationException e) {
            logger.error("Error in internal notification: {}", e.toString());
        }

        logger.debug("Notification message handled");
    }


    private void sendExternalNotifications(UUID notificationInstanceUUID, NotificationMessage notificationMessage) throws ConnectorException, ValidationException {
        NotificationInstanceReference notificationInstanceReference = notificationInstanceReferenceRepository.findByUuid(notificationInstanceUUID).orElseThrow(() -> new NotFoundException(NotificationInstanceReference.class, notificationInstanceUUID));

        List<DataAttribute> mappingAttributes;
        ConnectorDto connector = notificationInstanceReference.getConnector().mapToDto();
        try {
            mappingAttributes = notificationInstanceApiClient.listMappingAttributes(connector, notificationInstanceReference.getKind());
        } catch (ConnectorException e) {
            logger.error("Cannot retrieve mapping attributes from connector: " + e.getMessage());
            throw e;
        }

        List<NotificationRecipientDto> recipientsDto = new ArrayList<>();
        for (NotificationRecipient recipient : notificationMessage.getRecipients()) {
            logger.debug("Processing recipient {} of type {}.", recipient.getRecipientUuid(), recipient.getRecipientType());
            NotificationRecipientDto recipientDto = null;
            List<ResponseAttributeDto> recipientCustomAttributes = List.of();

            if (recipient.getRecipientType().equals(RecipientTypeEnum.USER)) {
                UUID recipientUuid = recipient.getRecipientUuid();
                try {
                    UserDetailDto userDetailDto = userManagementApiClient.getUserDetail(recipientUuid.toString());
                    recipientDto = new NotificationRecipientDto();
                    recipientDto.setEmail(userDetailDto.getEmail());
                    recipientDto.setName(userDetailDto.getUsername());

                    recipientCustomAttributes = attributeService.getCustomAttributesWithValues(recipientUuid, Resource.USER);
                } catch (Exception e) {
                    logger.warn("User with UUID {} was not found, notification was not sent for this user.", recipientUuid);
                }
            }
            if (recipient.getRecipientType().equals(RecipientTypeEnum.ROLE)) {
                UUID roleUuid = recipient.getRecipientUuid();
                try {
                    RoleDetailDto roleDetailDto = roleManagementApiClient.getRoleDetail(roleUuid.toString());
                    recipientDto = new NotificationRecipientDto();
                    recipientDto.setEmail(roleDetailDto.getEmail());
                    recipientDto.setName(roleDetailDto.getName());

                    recipientCustomAttributes = attributeService.getCustomAttributesWithValues(roleUuid, Resource.ROLE);
                } catch (Exception e) {
                    logger.warn("Role with UUID {} was not found, notification was not sent for this role.", roleUuid);
                }
            }
            if (recipient.getRecipientType().equals(RecipientTypeEnum.GROUP)) {
                UUID groupUuid = recipient.getRecipientUuid();
                Optional<Group> group = groupRepository.findByUuid(groupUuid);
                if (group.isPresent()) {
                    NotificationRecipientDto groupDto = new NotificationRecipientDto();
                    groupDto.setName(group.get().getName());
                    groupDto.setEmail(group.get().getEmail());

                    recipientCustomAttributes = attributeService.getCustomAttributesWithValues(groupUuid, Resource.GROUP);
                } else {
                    logger.warn("Group with UUID {} was not found, notification was not sent for this group.", groupUuid);
                }
            }

            if (recipientDto != null) {
                logger.debug("Setting mapped attributes for recipient {} of type {}.", recipientDto.getName(), recipient.getRecipientType());

                // prepare mapped attributes
                List<RequestAttributeDto> mappedAttributes = new ArrayList<>();
                HashMap<String, ResponseAttributeDto> mappedContent = new HashMap<>();
                for (NotificationInstanceMappedAttributes mappedAttribute : notificationInstanceReference.getMappedAttributes()) {
                    Optional<ResponseAttributeDto> recipientCustomAttribute = recipientCustomAttributes.stream().filter(c -> c.getUuid().toString().equals(mappedAttribute.getAttributeDefinitionUuid().toString())).findFirst();
                    if (recipientCustomAttribute.isPresent()) {
                        mappedContent.put(mappedAttribute.getMappingAttributeUuid().toString(), recipientCustomAttribute.get());
                    }
                }

                for (DataAttribute mappingAttribute : mappingAttributes) {
                    ResponseAttributeDto recipientCustomAttribute = mappedContent.get(mappingAttribute.getUuid());

                    if (recipientCustomAttribute == null) {
                        if (mappingAttribute.getProperties().isRequired()) {
                            throw new ValidationException(String.format("Missing mapping attribute %s with UUID %s in recipient custom attributes.", mappingAttribute.getName(), mappingAttribute.getUuid()));
                        }
                        continue;
                    }

                    if (!mappingAttribute.getContentType().equals(recipientCustomAttribute.getContentType())) {
                        throw new ValidationException(String.format("Mapped custom attribute %s with UUID %s has different content type (%s) as mapping attribute %s with UUID %s (%s).",
                                recipientCustomAttribute.getName(), recipientCustomAttribute.getUuid(), recipientCustomAttribute.getContentType().getLabel(),
                                mappingAttribute.getName(), mappingAttribute.getUuid(), mappingAttribute.getContentType().getLabel()));
                    }

                    RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
                    requestAttributeDto.setUuid(mappingAttribute.getUuid());
                    requestAttributeDto.setName(mappingAttribute.getName());
                    requestAttributeDto.setContent(recipientCustomAttribute.getContent());
                    mappedAttributes.add(requestAttributeDto);
                }
                recipientDto.setMappedAttributes(mappedAttributes);
                recipientsDto.add(recipientDto);
            }
        }

        if (!recipientsDto.isEmpty()) {
            NotificationProviderNotifyRequestDto notificationProviderNotifyRequestDto = new NotificationProviderNotifyRequestDto();
            notificationProviderNotifyRequestDto.setNotificationData(notificationMessage.getData());
            notificationProviderNotifyRequestDto.setResource(notificationMessage.getResource());
            notificationProviderNotifyRequestDto.setEventType(notificationMessage.getType());
            notificationProviderNotifyRequestDto.setRecipients(recipientsDto);

            try {
                notificationInstanceApiClient.sendNotification(connector, notificationInstanceReference.getNotificationInstanceUuid().toString(), notificationProviderNotifyRequestDto);
            } catch (ConnectorException e) {
                logger.error("Cannot send notification to connector: " + e.getMessage());
                throw e;
            }
        } else {
            logger.info("No recipients were provided, notifications were not sent.");
        }
    }

    private void sendInternalNotifications(NotificationMessage notificationMessage) throws ValidationException {
        String[] messageAndDetail = getNotificationMessageAndDetail(notificationMessage);

        String message = messageAndDetail[0];
        String detail = messageAndDetail[1];

        for (NotificationRecipient recipient : notificationMessage.getRecipients()) {
            switch (recipient.getRecipientType()) {
                case USER -> notificationService.createNotificationForUser(message,
                        detail,
                        recipient.getRecipientUuid().toString(),
                        notificationMessage.getResource(),
                        notificationMessage.getResourceUUID() != null ? notificationMessage.getResourceUUID().toString() : null);
                case ROLE -> notificationService.createNotificationForRole(message,
                        detail,
                        recipient.getRecipientUuid().toString(),
                        notificationMessage.getResource(),
                        notificationMessage.getResourceUUID() != null ? notificationMessage.getResourceUUID().toString() : null);
                case GROUP -> notificationService.createNotificationForGroup(message,
                        detail,
                        recipient.getRecipientUuid().toString(),
                        notificationMessage.getResource(),
                        notificationMessage.getResourceUUID() != null ? notificationMessage.getResourceUUID().toString() : null);
                default ->
                        throw new ValidationException("Unhandled recipient type for internal notification: " + recipient.getRecipientType());
            }
        }
    }

    private String[] getNotificationMessageAndDetail(NotificationMessage notificationMessage) throws ValidationException {
        String[] result = new String[2];
        NotificationType type = notificationMessage.getType();
        Object notificationData = mapper.convertValue(notificationMessage.getData(), notificationMessage.getType().getNotificationData());

        switch (type) {
            case TEXT -> {
                NotificationDataText data = (NotificationDataText) notificationData;
                result[0] = data.getText();
                result[1] = data.getDetail();
            }
            case CERTIFICATE_STATUS_CHANGED -> {
                NotificationDataCertificateStatusChanged data = (NotificationDataCertificateStatusChanged) notificationData;
                result[0] = String.format("Certificate status changed from %s to %s for certificate identified as '%s' with serial number '%s' issued by '%s'",
                        data.getOldStatus(), data.getNewStatus(), data.getSubjectDn(), data.getSerialNumber(), data.getIssuerDn());
            }
            case CERTIFICATE_ACTION_PERFORMED -> {
                NotificationDataCertificateActionPerformed data = (NotificationDataCertificateActionPerformed) notificationData;
                boolean failed = data.getErrorMessage() != null;
                result[0] = String.format("Certificate action %s %s for certificate identified as '%s'", data.getAction(), failed ? "failed" : "successful", data.getSubjectDn());
                result[1] = failed ? "Error message: " + data.getErrorMessage() : String.format("Certificate serial number '%s' issued by '%s'", data.getSerialNumber(), data.getIssuerDn());
            }
            case SCHEDULED_JOB_COMPLETED -> {
                NotificationDataScheduledJobCompleted data = (NotificationDataScheduledJobCompleted) notificationData;
                result[0] = String.format("%s scheduled task has finished for %s with result %s", data.getJobType(), data.getJobName(), data.getStatus());
            }
            case APPROVAL_REQUESTED -> {
                NotificationDataApproval data = (NotificationDataApproval) notificationData;
                result[0] = String.format("Request %s for %s from %s is waiting to be approved until %s", data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getExpiryAt());
                result[1] = getApprovalNotificationDetail(data);
            }
            case APPROVAL_CLOSED -> {
                NotificationDataApproval data = (NotificationDataApproval) notificationData;
                result[0] = String.format("Request %s for %s from %s is %s", data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getStatus().getLabel());
                result[1] = getApprovalNotificationDetail(data);
            }
            default -> throw new ValidationException("Unhandled notification type for internal notification: " + type);
        }

        return result;
    }

    private String getApprovalNotificationDetail(NotificationDataApproval approvalData) {
        return String.format("Approval profile name: %s,\nResource: %s,\nResource action: %s,\nObject UUID: %s",
                approvalData.getApprovalProfileName(), approvalData.getResource().getLabel(), approvalData.getResourceAction(), approvalData.getObjectUuid());
    }

}

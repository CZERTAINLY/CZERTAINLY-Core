package com.czertainly.core.messaging.listeners;

import com.czertainly.api.clients.NotificationInstanceApiClient;
import com.czertainly.api.exception.ConnectorEntityNotFoundException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.events.data.*;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.api.model.connector.notification.NotificationRecipientDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.RoleDetailDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.notifications.*;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.notifications.NotificationInstanceReferenceRepository;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.core.dao.repository.notifications.NotificationProfileVersionRepository;
import com.czertainly.core.dao.repository.notifications.PendingNotificationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.NotificationService;
import com.czertainly.core.service.ResourceObjectAssociationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@Transactional
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);

    private ObjectMapper mapper;
    private AttributeEngine attributeEngine;

    private NotificationService notificationService;
    private NotificationInstanceApiClient notificationInstanceApiClient;
    private PendingNotificationRepository pendingNotificationRepository;
    private NotificationProfileVersionRepository notificationProfileVersionRepository;
    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;

    private GroupRepository groupRepository;
    private UserManagementApiClient userManagementApiClient;
    private RoleManagementApiClient roleManagementApiClient;
    private ResourceObjectAssociationService resourceObjectAssociationService;

    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired
    public void setNotificationInstanceApiClient(NotificationInstanceApiClient notificationInstanceApiClient) {
        this.notificationInstanceApiClient = notificationInstanceApiClient;
    }

    @Autowired
    public void setPendingNotificationRepository(PendingNotificationRepository pendingNotificationRepository) {
        this.pendingNotificationRepository = pendingNotificationRepository;
    }

    @Autowired
    public void setNotificationProfileVersionRepository(NotificationProfileVersionRepository notificationProfileVersionRepository) {
        this.notificationProfileVersionRepository = notificationProfileVersionRepository;
    }

    @Autowired
    public void setNotificationInstanceReferenceRepository(NotificationInstanceReferenceRepository notificationInstanceReferenceRepository) {
        this.notificationInstanceReferenceRepository = notificationInstanceReferenceRepository;
    }

    @Autowired
    public void setGroupRepository(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
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
    public void setResourceObjectAssociationService(ResourceObjectAssociationService resourceObjectAssociationService) {
        this.resourceObjectAssociationService = resourceObjectAssociationService;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_NOTIFICATIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(NotificationMessage message) {
        logger.debug("Received notification message: {}", message);

        if (message.getNotificationProfileUuid() == null) {
            try {
                sendInternalNotifications(message);
            } catch (ValidationException e) {
                logger.error("Error in internal notification: {}", e.toString());
            }
        } else {
            try {
                sendByNotificationProfile(message);
            } catch (Exception e) {
                logger.error("Error in sending notifications based on notification profile: {}", e.toString());
            }
        }

        logger.debug("Notification message handled");
    }

    private void sendByNotificationProfile(NotificationMessage message) throws NotFoundException {
        boolean proceedWithNotifying = false;
        NotificationProfileVersion notificationProfileVersion;
        PendingNotification pendingNotification = pendingNotificationRepository.findByNotificationProfileUuidAndResourceAndObjectUuidAndEvent(message.getNotificationProfileUuid(), message.getResource(), message.getObjectUuid(), message.getEvent());
        if (pendingNotification == null) {
            proceedWithNotifying = true;
            notificationProfileVersion = notificationProfileVersionRepository.findTopByNotificationProfileUuidOrderByVersionDesc(message.getNotificationProfileUuid()).orElseThrow(() -> new NotFoundException(NotificationProfile.class, message.getNotificationProfileUuid()));
            if (notificationProfileVersion.getFrequency() != null || notificationProfileVersion.getRepetitions() != null) {
                pendingNotification = new PendingNotification();
                pendingNotification.setNotificationProfileUuid(notificationProfileVersion.getNotificationProfileUuid());
                pendingNotification.setVersion(notificationProfileVersion.getVersion());
                pendingNotification.setEvent(message.getEvent());
                pendingNotification.setResource(message.getResource());
                pendingNotification.setObjectUuid(message.getObjectUuid());
                pendingNotification.setRepetitions(0);
            }
        } else {
            OffsetDateTime now = OffsetDateTime.now();
            notificationProfileVersion = notificationProfileVersionRepository.findByNotificationProfileUuidAndVersion(message.getNotificationProfileUuid(), pendingNotification.getVersion()).orElseThrow(() -> new NotFoundException(NotificationProfile.class, message.getNotificationProfileUuid()));
            proceedWithNotifying = (notificationProfileVersion.getFrequency() == null || Duration.between(pendingNotification.getLastSentAt(), now).getNano() > notificationProfileVersion.getFrequency().getNano())
                    && (notificationProfileVersion.getRepetitions() == null || pendingNotification.getRepetitions() == null || pendingNotification.getRepetitions() < notificationProfileVersion.getRepetitions());
        }

        if (!proceedWithNotifying) {
            logger.debug("Notification suppressed by configuration of notification profile {} for event {}. Notification send last time {} and was repeated {} times.", notificationProfileVersion.getNotificationProfile().getName(), message.getEvent(), pendingNotification.getLastSentAt().toString(), pendingNotification.getRepetitions());
            return;
        }

        List<NotificationRecipient> recipients = getRecipients(notificationProfileVersion.getRecipientType(), notificationProfileVersion.getRecipientUuid(), message.getResource(), message.getObjectUuid());

        // send external notification
        if (notificationProfileVersion.getNotificationInstanceRefUuid() != null) {
            UUID notificationInstanceUUID = notificationProfileVersion.getNotificationInstanceRefUuid();
            logger.debug("Sending notification message externally. Notification instance UUID: {}", notificationInstanceUUID);
            try {
                sendExternalNotifications(notificationInstanceUUID, recipients, message.getData(), message.getEvent(), message.getResource());
                logger.debug("Sending notification message externally successful.");
            } catch (ConnectorEntityNotFoundException e) {
                logger.warn("Notification instance {} configured for notification profile {} in event {} was not found.", notificationInstanceUUID, notificationProfileVersion.getNotificationProfile().getName(), message.getEvent());
            } catch (ConnectorException e) {
                logger.warn("Error in sending request to connector of notification instance {} configured for notification profile {} in event {}: {}", notificationInstanceUUID, notificationProfileVersion.getNotificationProfile().getName(), message.getEvent(), e.getMessage(), e);
            } catch (ValidationException e) {
                logger.warn("Validation error in sending notification to connector of notification instance {} configured for notification profile {} in event {}: {}", notificationInstanceUUID, notificationProfileVersion.getNotificationProfile().getName(), message.getEvent(), e.getMessage());
            } catch (Exception e) {
                logger.error("Error in external notification with notification instance {} configured for notification profile {} in event {}: {}", notificationInstanceUUID, notificationProfileVersion.getNotificationProfile().getName(), message.getEvent(), e.toString());
            }
        }

        // send internal notification
        if (notificationProfileVersion.isInternalNotification()) {
            try {
                sendInternalNotifications(recipients, getInternalNotificationData(message), message.getResource(), message.getObjectUuid());
            } catch (ValidationException e) {
                logger.error("Error in internal notification: {}", e.toString());
            }
        }

        if (pendingNotification != null) {
            pendingNotification.setRepetitions(pendingNotification.getRepetitions() + 1);
            pendingNotificationRepository.save(pendingNotification);
        }
    }

    private List<NotificationRecipient> getRecipients(RecipientType recipientType, UUID recipientUuid, Resource resource, UUID objectUuid) {
        if (recipientType != RecipientType.OWNER) {
            return List.of(new NotificationRecipient(recipientType, recipientUuid));
        }

        NameAndUuidDto ownerInfo = resourceObjectAssociationService.getOwner(resource, objectUuid);
        if (ownerInfo == null) return List.of();

        return List.of(new NotificationRecipient(RecipientType.OWNER, UUID.fromString(ownerInfo.getUuid())));
    }


    private void sendExternalNotifications(UUID notificationInstanceUUID, List<NotificationRecipient> recipients, Object notificationData, ResourceEvent event, Resource resource) throws ConnectorException, ValidationException, NotFoundException {
        NotificationInstanceReference notificationInstanceReference = notificationInstanceReferenceRepository.findByUuid(notificationInstanceUUID).orElseThrow(() -> new NotFoundException(NotificationInstanceReference.class, notificationInstanceUUID));

        List<DataAttribute> mappingAttributes;
        ConnectorDto connector = notificationInstanceReference.getConnector().mapToDto();
        try {
            mappingAttributes = notificationInstanceApiClient.listMappingAttributes(connector, notificationInstanceReference.getKind());
        } catch (ConnectorException e) {
            logger.error("Cannot retrieve mapping attributes from connector: {}", e.getMessage());
            throw e;
        }

        List<NotificationRecipientDto> recipientsDto = new ArrayList<>();
        for (NotificationRecipient recipient : recipients) {
            logger.debug("Processing recipient {} of type {}.", recipient.getRecipientUuid(), recipient.getRecipientType());
            NotificationRecipientDto recipientDto = null;
            List<ResponseAttributeDto> recipientCustomAttributes = List.of();

            if (recipient.getRecipientType().equals(RecipientType.USER)) {
                UUID recipientUuid = recipient.getRecipientUuid();
                try {
                    UserDetailDto userDetailDto = userManagementApiClient.getUserDetail(recipientUuid.toString());
                    recipientDto = new NotificationRecipientDto();
                    recipientDto.setEmail(userDetailDto.getEmail());
                    recipientDto.setName(userDetailDto.getUsername());

                    recipientCustomAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.USER, recipientUuid);
                } catch (Exception e) {
                    logger.warn("User with UUID {} was not found, notification was not sent for this user.", recipientUuid);
                }
            }
            if (recipient.getRecipientType().equals(RecipientType.ROLE)) {
                UUID roleUuid = recipient.getRecipientUuid();
                try {
                    RoleDetailDto roleDetailDto = roleManagementApiClient.getRoleDetail(roleUuid.toString());
                    String email = roleDetailDto.getEmail();
                    if (email == null || email.isBlank()) {
                        logger.warn("Role with UUID {} does not have specified email, notification was not sent for this role.", roleUuid);
                    } else {
                        recipientDto = new NotificationRecipientDto();
                        recipientDto.setEmail(email);
                        recipientDto.setName(roleDetailDto.getName());
                    }

                    recipientCustomAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.ROLE, roleUuid);
                } catch (Exception e) {
                    logger.warn("Role with UUID {} was not found, notification was not sent for this role.", roleUuid);
                }
            }
            if (recipient.getRecipientType().equals(RecipientType.GROUP)) {
                UUID groupUuid = recipient.getRecipientUuid();
                Optional<Group> group = groupRepository.findByUuid(groupUuid);
                if (group.isPresent()) {
                    String email = group.get().getEmail();
                    if (email == null || email.isBlank()) {
                        logger.warn("Group with UUID {} does not have specified email, notification was not sent for this group.", groupUuid);
                    } else {
                        recipientDto = new NotificationRecipientDto();
                        recipientDto.setName(group.get().getName());
                        recipientDto.setEmail(email);
                    }

                    recipientCustomAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.GROUP, groupUuid);
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
                    Optional<ResponseAttributeDto> recipientCustomAttribute = recipientCustomAttributes.stream().filter(c -> c.getUuid().equals(mappedAttribute.getAttributeDefinitionUuid().toString())).findFirst();
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
                    requestAttributeDto.setContentType(mappingAttribute.getContentType());
                    requestAttributeDto.setContent(recipientCustomAttribute.getContent());
                    mappedAttributes.add(requestAttributeDto);
                }
                recipientDto.setMappedAttributes(mappedAttributes);
                recipientsDto.add(recipientDto);
            }
        }

        if (!recipientsDto.isEmpty()) {
            NotificationProviderNotifyRequestDto notificationProviderNotifyRequestDto = new NotificationProviderNotifyRequestDto();
            notificationProviderNotifyRequestDto.setNotificationData(notificationData);
            notificationProviderNotifyRequestDto.setResource(resource);
            notificationProviderNotifyRequestDto.setEvent(event);
            notificationProviderNotifyRequestDto.setRecipients(recipientsDto);

            try {
                notificationInstanceApiClient.sendNotification(connector, notificationInstanceReference.getNotificationInstanceUuid().toString(), notificationProviderNotifyRequestDto);
            } catch (ConnectorException e) {
                logger.error("Cannot send notification to connector: {}", e.getMessage());
                throw e;
            }
        } else {
            logger.info("No recipients were provided, notifications were not sent.");
        }
    }

    private void sendInternalNotifications(NotificationMessage message) throws ValidationException {
        InternalNotificationEventData notificationData;
        try {
            notificationData = mapper.convertValue(message.getData(), InternalNotificationEventData.class);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("NotificationMessage for internal notification contains invalid data.");
        }

        sendInternalNotifications(message.getRecipients(), notificationData, message.getResource(), message.getObjectUuid());
    }

    private void sendInternalNotifications(List<NotificationRecipient> recipients, InternalNotificationEventData notificationData, Resource resource, UUID objectUuid) {
        for (NotificationRecipient recipient : recipients) {
            switch (recipient.getRecipientType()) {
                case USER ->
                        notificationService.createNotificationForUser(notificationData.getText(), notificationData.getDetail(),
                                recipient.getRecipientUuid().toString(),
                                resource, objectUuid != null ? objectUuid.toString() : null);
                case ROLE ->
                        notificationService.createNotificationForRole(notificationData.getText(), notificationData.getDetail(),
                                recipient.getRecipientUuid().toString(),
                                resource, objectUuid != null ? objectUuid.toString() : null);
                case GROUP ->
                        notificationService.createNotificationForGroup(notificationData.getText(), notificationData.getDetail(),
                                recipient.getRecipientUuid().toString(),
                                resource, objectUuid != null ? objectUuid.toString() : null);
                default ->
                        throw new ValidationException("Unhandled recipient type for internal notification: " + recipient.getRecipientType());
            }
        }
    }

    private InternalNotificationEventData getInternalNotificationData(NotificationMessage message) throws ValidationException {
        Class<? extends EventData> dataClazz = message.getEvent() == null ? InternalNotificationEventData.class : message.getEvent().getEventData();

        Object notificationData;
        try {
            notificationData = mapper.convertValue(message.getData(), dataClazz);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("NotificationMessage for internal notification contains invalid data. Expected: " + dataClazz.getName());
        }

        if (message.getEvent() == null) {
            return (InternalNotificationEventData) notificationData;
        }

        return switch (message.getEvent()) {
            case CERTIFICATE_STATUS_CHANGED -> {
                CertificateStatusChangedEventData data = (CertificateStatusChangedEventData) notificationData;
                yield new InternalNotificationEventData("Certificate validation status changed from %s to %s for certificate identified as '%s' with serial number '%s' issued by '%s'"
                        .formatted(data.getOldStatus(), data.getNewStatus(), data.getSubjectDn(), data.getSerialNumber(), data.getIssuerDn()), null);
            }
            case CERTIFICATE_ACTION_PERFORMED -> {
                CertificateActionPerformedEventData data = (CertificateActionPerformedEventData) notificationData;
                boolean failed = data.getErrorMessage() != null;
                yield new InternalNotificationEventData("Certificate action %s %s for certificate identified as '%s'".formatted(data.getAction(), failed ? "failed" : "successful", data.getSubjectDn()),
                        failed ? "Error message: " + data.getErrorMessage() : "Certificate serial number '%s' issued by '%s'".formatted(data.getSerialNumber(), data.getIssuerDn()));
            }
            case CERTIFICATE_DISCOVERED -> {
                CertificateDiscoveredEventData data = (CertificateDiscoveredEventData) notificationData;
                yield new InternalNotificationEventData("Certificate identified as '%s' with serial number '%s' issued by '%s' discovered by '%s' discovery".formatted(data.getSubjectDn(), data.getSerialNumber(), data.getIssuerDn(), data.getDiscoveryName()),
                        "Discovery Connector: %s".formatted(data.getDiscoveryConnectorName() == null ? data.getDiscoveryConnectorUuid() : data.getDiscoveryConnectorName()));
            }
            case DISCOVERY_FINISHED -> {
                DiscoveryFinishedEventData data = (DiscoveryFinishedEventData) notificationData;
                yield new InternalNotificationEventData("Discovery %s has finished with status %s".formatted(data.getDiscoveryName(), data.getDiscoveryStatus().getLabel()), data.getDiscoveryMessage());
            }
            case APPROVAL_REQUESTED -> {
                ApprovalEventData data = (ApprovalEventData) notificationData;
                yield new InternalNotificationEventData("Request %s for %s from %s is waiting to be approved until %s".formatted(data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getExpiryAt()),
                        getApprovalNotificationDetail(data));
            }
            case APPROVAL_CLOSED -> {
                ApprovalEventData data = (ApprovalEventData) notificationData;
                yield new InternalNotificationEventData("Request %s for %s from %s is %s".formatted(data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getStatus().getLabel()),
                        getApprovalNotificationDetail(data));
            }
            case SCHEDULED_JOB_FINISHED -> {
                ScheduledJobFinishedEventData data = (ScheduledJobFinishedEventData) notificationData;
                yield new InternalNotificationEventData("%s scheduled task has finished for %s with result %s".formatted(data.getJobType(), data.getJobName(), data.getStatus()), null);
            }
        };
    }

    private String getApprovalNotificationDetail(ApprovalEventData approvalData) {
        return String.format("Approval profile name: %s, Resource: %s, Resource action: %s, Object UUID: %s",
                approvalData.getApprovalProfileName(), approvalData.getResource().getLabel(), approvalData.getResourceAction(), approvalData.getObjectUuid());
    }

}

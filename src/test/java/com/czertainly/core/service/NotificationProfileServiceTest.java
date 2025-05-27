package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.NotificationProfileController;
import com.czertainly.api.model.client.connector.ConnectorRequestDto;
import com.czertainly.api.model.client.notification.NotificationProfileDetailDto;
import com.czertainly.api.model.client.notification.NotificationProfileRequestDto;
import com.czertainly.api.model.client.notification.NotificationProfileResponseDto;
import com.czertainly.api.model.client.notification.NotificationProfileUpdateRequestDto;
import com.czertainly.api.model.common.events.data.ScheduledJobFinishedEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.notification.NotificationInstanceRequestDto;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.notifications.NotificationInstanceReference;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.notifications.NotificationInstanceReferenceRepository;
import com.czertainly.core.messaging.listeners.NotificationListener;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

class NotificationProfileServiceTest extends BaseSpringBootTest {

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10001");
    }

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private GroupService groupService;

    @Autowired
    private NotificationListener notificationListener;

    @Autowired
    private NotificationProfileService notificationProfileService;

    @Autowired
    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;

    private NotificationProfileDetailDto originalNotificationProfile;

    @BeforeEach
    public void setUp() throws NotFoundException, AlreadyExistException {
        NotificationProfileRequestDto requestDto = new NotificationProfileRequestDto();
        requestDto.setName("TestProfileFirst");
        requestDto.setRecipientType(RecipientType.OWNER);
        requestDto.setInternalNotification(true);
        originalNotificationProfile = notificationProfileService.createNotificationProfile(requestDto);
    }

    @Test
    void testCreateNotificationProfile() throws NotFoundException, AlreadyExistException, AttributeException {
        WireMockServer mockServer = new WireMockServer();
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/notificationProvider/[^/]+/attributes/mapping")).willReturn(WireMock.okJson("[]")));

        GroupRequestDto groupRequestDto = new GroupRequestDto();
        groupRequestDto.setName("Test group");
        GroupDto groupDto = groupService.createGroup(groupRequestDto);

        Connector connector = new Connector();
        connector.setName("notificationInstanceConnector");
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        NotificationInstanceReference instance = new NotificationInstanceReference();
        instance.setName("TestNotificationInstance");
        instance.setKind("EMAIL");
        instance.setConnectorUuid(connector.getUuid());
        instance.setNotificationInstanceUuid(UUID.randomUUID());
        notificationInstanceReferenceRepository.save(instance);

        NotificationProfileRequestDto requestDto = new NotificationProfileRequestDto();
        requestDto.setName(originalNotificationProfile.getName());
        requestDto.setRecipientType(RecipientType.GROUP);
        requestDto.setRecipientUuids(List.of(UUID.fromString(groupDto.getUuid())));
        requestDto.setRepetitions(1);
        requestDto.setInternalNotification(false);
        requestDto.setNotificationInstanceUuid(instance.getUuid());
        Assertions.assertThrows(AlreadyExistException.class, () -> notificationProfileService.createNotificationProfile(requestDto));

        requestDto.setName("TestProfile");
        NotificationProfileDetailDto notificationProfileDetailDto = notificationProfileService.createNotificationProfile(requestDto);

        Assertions.assertEquals(1, notificationProfileDetailDto.getVersion());
        Assertions.assertEquals(RecipientType.GROUP, notificationProfileDetailDto.getRecipients().getFirst().getType());
        Assertions.assertEquals(groupDto.getUuid(), notificationProfileDetailDto.getRecipients().getFirst().getUuid());

        // check for same result when retrieving detail by UUID
        notificationProfileDetailDto = notificationProfileService.getNotificationProfile(SecuredUUID.fromString(notificationProfileDetailDto.getUuid()), null);
        Assertions.assertEquals(1, notificationProfileDetailDto.getVersion());
        Assertions.assertEquals(RecipientType.GROUP, notificationProfileDetailDto.getRecipients().getFirst().getType());
        Assertions.assertEquals(groupDto.getUuid(), notificationProfileDetailDto.getRecipients().getFirst().getUuid());

        NotificationProfileResponseDto responseDto = notificationProfileService.listNotificationProfiles(new PaginationRequestDto());
        Assertions.assertEquals(2, responseDto.getTotalItems());

        NotificationMessage notificationMessage = new NotificationMessage(ResourceEvent.SCHEDULED_JOB_FINISHED, Resource.SCHEDULED_JOB,
                UUID.randomUUID(), List.of(UUID.fromString(notificationProfileDetailDto.getUuid())), List.of(), new ScheduledJobFinishedEventData("Test job", "JobType", "Finished", UUID.randomUUID()));
        Assertions.assertDoesNotThrow(() -> notificationListener.processMessage(notificationMessage));

        instance.setKind("OTHER_KIND");
        notificationInstanceReferenceRepository.save(instance);
        Assertions.assertDoesNotThrow(() -> notificationListener.processMessage(notificationMessage));

        mockServer.shutdown();
    }

    @Test
    void testUpdateNotificationProfile() throws NotFoundException {
        WireMockServer mockServer = new WireMockServer(10001);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        UUID roleUuid = UUID.randomUUID();
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/roles/[^/]+")).willReturn(
                WireMock.okJson("""
                        {
                            "uuid": "%s",
                            "name": "TestRole",
                            "systemRole": false
                        },
                        """.formatted(roleUuid.toString()))
        ));

        NotificationProfileUpdateRequestDto requestDto = new NotificationProfileUpdateRequestDto();
        requestDto.setDescription("Updated description");
        requestDto.setRecipientType(RecipientType.OWNER);
        requestDto.setInternalNotification(true);
        NotificationProfileDetailDto updatedNotificationProfileDetailDto = notificationProfileService.editNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid()), requestDto);
        Assertions.assertEquals(originalNotificationProfile.getVersion(), updatedNotificationProfileDetailDto.getVersion(), "Versions should not change, no change in profile props");

        requestDto.setFrequency(Duration.ofDays(1));
        requestDto.setRepetitions(5);
        requestDto.setRecipientType(RecipientType.ROLE);
        requestDto.setRecipientUuids(List.of(roleUuid));
        updatedNotificationProfileDetailDto = notificationProfileService.editNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid()), requestDto);
        Assertions.assertEquals(originalNotificationProfile.getVersion() + 1, updatedNotificationProfileDetailDto.getVersion(), "Versions should change, updated profile props");
        Assertions.assertEquals(requestDto.getRecipientType(), updatedNotificationProfileDetailDto.getRecipients().getFirst().getType(), "Recipient type should be correct");
        Assertions.assertEquals(roleUuid.toString(), updatedNotificationProfileDetailDto.getRecipients().getFirst().getUuid(), "Recipient type should be correct");

        NotificationProfileDetailDto olderVersion = notificationProfileService.getNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid()), originalNotificationProfile.getVersion());
        Assertions.assertEquals(originalNotificationProfile.getVersion(), olderVersion.getVersion());
        Assertions.assertEquals(originalNotificationProfile.getRecipients().getFirst().getType(), olderVersion.getRecipients().getFirst().getType());

        mockServer.shutdown();
    }

    @Test
    void testDeleteNotificationProfile() {
        Assertions.assertThrows(NotFoundException.class, () -> notificationProfileService.deleteNotificationProfile(SecuredUUID.fromUUID(UUID.randomUUID())));
        Assertions.assertDoesNotThrow(() -> notificationProfileService.deleteNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid())));
    }

}

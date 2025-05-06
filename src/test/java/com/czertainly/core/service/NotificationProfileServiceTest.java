package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.NotificationProfileController;
import com.czertainly.api.model.client.notification.NotificationProfileDetailDto;
import com.czertainly.api.model.client.notification.NotificationProfileRequestDto;
import com.czertainly.api.model.client.notification.NotificationProfileResponseDto;
import com.czertainly.api.model.client.notification.NotificationProfileUpdateRequestDto;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

class NotificationProfileServiceTest extends BaseSpringBootTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private NotificationProfileController notificationProfileController;

    @Autowired
    private NotificationProfileService notificationProfileService;

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
        GroupRequestDto groupRequestDto = new GroupRequestDto();
        groupRequestDto.setName("Test group");
        GroupDto groupDto = groupService.createGroup(groupRequestDto);

        NotificationProfileRequestDto requestDto = new NotificationProfileRequestDto();
        requestDto.setName("TestProfile");
        requestDto.setRecipientType(RecipientType.GROUP);
        requestDto.setRecipientUuid(UUID.fromString(groupDto.getUuid()));
        requestDto.setRepetitions(1);
        requestDto.setInternalNotification(true);
        NotificationProfileDetailDto notificationProfileDetailDto = notificationProfileService.createNotificationProfile(requestDto);

        Assertions.assertEquals(1, notificationProfileDetailDto.getVersion());
        Assertions.assertEquals(RecipientType.GROUP, notificationProfileDetailDto.getRecipient().getType());
        Assertions.assertEquals(groupDto.getUuid(), notificationProfileDetailDto.getRecipient().getUuid());

        // check for same result when retrieving detail by UUID
        notificationProfileDetailDto = notificationProfileService.getNotificationProfile(SecuredUUID.fromString(notificationProfileDetailDto.getUuid()), null);
        Assertions.assertEquals(1, notificationProfileDetailDto.getVersion());
        Assertions.assertEquals(RecipientType.GROUP, notificationProfileDetailDto.getRecipient().getType());
        Assertions.assertEquals(groupDto.getUuid(), notificationProfileDetailDto.getRecipient().getUuid());

        NotificationProfileResponseDto responseDto = notificationProfileService.listNotificationProfiles(new PaginationRequestDto());
        Assertions.assertEquals(2, responseDto.getTotalItems());
    }

    @Test
    void testUpdateNotificationProfile() throws NotFoundException, AlreadyExistException, AttributeException {
        NotificationProfileUpdateRequestDto requestDto = new NotificationProfileUpdateRequestDto();
        requestDto.setDescription("Updated description");
        requestDto.setRecipientType(RecipientType.OWNER);
        requestDto.setInternalNotification(true);
        NotificationProfileDetailDto updatedNotificationProfileDetailDto = notificationProfileService.editNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid()), requestDto);
        Assertions.assertEquals(originalNotificationProfile.getVersion(), updatedNotificationProfileDetailDto.getVersion(), "Versions should not change, no change in profile props");

        GroupRequestDto groupRequestDto = new GroupRequestDto();
        groupRequestDto.setName("Test group");
        GroupDto groupDto = groupService.createGroup(groupRequestDto);

        requestDto.setFrequency(Duration.ofDays(1));
        requestDto.setRepetitions(5);
        requestDto.setRecipientType(RecipientType.GROUP);
        requestDto.setRecipientUuid(UUID.fromString(groupDto.getUuid()));
        updatedNotificationProfileDetailDto = notificationProfileService.editNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid()), requestDto);
        Assertions.assertEquals(originalNotificationProfile.getVersion() + 1, updatedNotificationProfileDetailDto.getVersion(), "Versions should change, updated profile props");

        NotificationProfileDetailDto olderVersion = notificationProfileService.getNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid()), originalNotificationProfile.getVersion());
        Assertions.assertEquals(originalNotificationProfile.getVersion(), olderVersion.getVersion());
        Assertions.assertEquals(originalNotificationProfile.getRecipient().getType(), olderVersion.getRecipient().getType());
    }

    @Test
    void testDeleteNotificationProfile() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> notificationProfileService.deleteNotificationProfile(SecuredUUID.fromUUID(UUID.randomUUID())));
        Assertions.assertDoesNotThrow(() -> notificationProfileService.deleteNotificationProfile(SecuredUUID.fromString(originalNotificationProfile.getUuid())));
    }

}

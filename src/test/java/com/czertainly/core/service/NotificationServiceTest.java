package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.notification.NotificationRequestDto;
import com.czertainly.api.model.client.notification.NotificationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.repository.NotificationRecipientRepository;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@SpringBootTest
@Transactional
@Rollback
class NotificationServiceTest extends BaseSpringBootTest {

    private static final int authServiceMockPort = 10001;
    private static final String mockRoleUuid = UUID.randomUUID().toString();
    private String mockUser1Uuid;
    private static final String mockUser2Uuid = UUID.randomUUID().toString();
    private static final String mockUser3Uuid = UUID.randomUUID().toString();
    private static final String mockGroup1Uuid = UUID.randomUUID().toString();
    private static final String mockGroup2Uuid = UUID.randomUUID().toString();

    private WireMockServer mockServer;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:" + authServiceMockPort);
    }

    @BeforeEach
    public void setUp() {
        var authInfo = AuthHelper.getUserIdentification();
        mockUser1Uuid = authInfo.getUuid();
    }

    @Test
    void testCreateNotificationsForRole() throws NotFoundException {
        setupAuthServiceMock();

        notificationService.createNotificationForRole("TestMessage", null, mockRoleUuid, Resource.DISCOVERY, UUID.randomUUID().toString());
        notificationService.createNotificationForGroup("TestMessage2", null, mockGroup1Uuid, Resource.DISCOVERY, "%s,%s".formatted(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        notificationService.createNotificationForUser("TestMessage3", null, mockUser1Uuid, Resource.DISCOVERY, UUID.randomUUID().toString());

        Assertions.assertEquals(6, notificationRecipientRepository.findAll().size());

        NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
        notificationRequestDto.setItemsPerPage(10);
        notificationRequestDto.setPageNumber(1);
        NotificationResponseDto listingResponse = notificationService.listNotifications(notificationRequestDto);

        Assertions.assertEquals(3, listingResponse.getItems().size());

        notificationService.markNotificationAsRead(listingResponse.getItems().getFirst().getUuid().toString());

        notificationRequestDto.setUnread(true);
        listingResponse = notificationService.listNotifications(notificationRequestDto);

        Assertions.assertEquals(2, listingResponse.getItems().size());

        mockServer.shutdown();
    }

    private void setupAuthServiceMock() {
        mockServer = new WireMockServer(authServiceMockPort);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        String listUsersMockResponse = """
                [
                     {
                         "uuid": "%s",
                         "username": "doejohn",
                         "firstName": "John",
                         "lastName": "Doe",
                         "groups": [{
                                 "uuid": "%s",
                                 "name": "TestGroup1"
                             }, {
                                 "uuid": "%s",
                                 "name": "TestGroup2"
                             }
                         ],
                         "enabled": true,
                         "systemUser": false
                     }, {
                         "uuid": "%s",
                         "username": "doejane",
                         "firstName": "Jane",
                         "lastName": "Doe",
                         "email": "jane.doe@example.com",
                         "groups": [{
                                 "uuid": "%s",
                                 "name": "TestGroup1"
                             }
                         ],
                         "enabled": true,
                         "systemUser": false
                     }, {
                         "uuid": "%s",
                         "username": "doejr",
                         "firstName": "Jr",
                         "lastName": "Doe",
                         "email": "jr.doe@example.com",
                         "groups": [{
                                 "uuid": "%s",
                                 "name": "TestGroup2"
                             }
                         ],
                         "enabled": true,
                         "systemUser": false
                     }
                ]
                """.formatted(mockUser1Uuid, mockGroup1Uuid, mockGroup2Uuid, mockUser2Uuid, mockGroup1Uuid, mockUser3Uuid, mockGroup2Uuid);

        String paginatedListUsersMockResponse = """
                {
                    "data": %s
                }
                """.formatted(listUsersMockResponse);

        String roleDetailMockResponse = """
                {
                  "uuid": "%s",
                  "name": "test-role",
                  "description": "Test role",
                  "systemRole": false,
                  "users": %s,
                  "customAttributes": []
                }
                """.formatted(mockRoleUuid, listUsersMockResponse);

        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/users")).willReturn(WireMock.okJson(paginatedListUsersMockResponse)));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/roles/%s".formatted(mockRoleUuid))).willReturn(WireMock.okJson(roleDetailMockResponse)));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/roles/%s/users".formatted(mockRoleUuid))).willReturn(WireMock.okJson(listUsersMockResponse)));
    }
}

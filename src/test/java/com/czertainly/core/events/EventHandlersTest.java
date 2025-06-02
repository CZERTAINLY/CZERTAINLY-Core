package com.czertainly.core.events;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.EventException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileRequestDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepRequestDto;
import com.czertainly.api.model.client.notification.NotificationProfileDetailDto;
import com.czertainly.api.model.client.notification.NotificationProfileRequestDto;
import com.czertainly.api.model.common.events.data.CertificateStatusChangedEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.settings.EventSettingsDto;
import com.czertainly.api.model.core.workflows.ExecutionType;
import com.czertainly.api.model.core.workflows.TriggerType;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.notifications.NotificationInstanceReference;
import com.czertainly.core.dao.entity.workflows.Action;
import com.czertainly.core.dao.entity.workflows.Execution;
import com.czertainly.core.dao.entity.workflows.ExecutionItem;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.notifications.NotificationInstanceReferenceRepository;
import com.czertainly.core.dao.repository.workflows.ActionRepository;
import com.czertainly.core.dao.repository.workflows.ExecutionItemRepository;
import com.czertainly.core.dao.repository.workflows.ExecutionRepository;
import com.czertainly.core.dao.repository.workflows.TriggerRepository;
import com.czertainly.core.events.data.DiscoveryResult;
import com.czertainly.core.events.handlers.*;
import com.czertainly.core.messaging.listeners.NotificationListener;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.service.*;
import com.czertainly.core.tasks.DiscoveryCertificateTask;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

class EventHandlersTest extends BaseSpringBootTest {

    @DynamicPropertySource
    static void authServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("auth-service.base-url", () -> "http://localhost:10001");
    }

    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateEventHistoryService certificateEventHistoryService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private CertificateStatusChangedEventHandler certificateStatusChangedEventHandler;
    @Autowired
    private CertificateActionPerformedEventHandler certificateActionPerformedEventHandler;

    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private ResourceObjectAssociationService associationService;

    @Autowired
    private ApprovalRepository approvalRepository;
    @Autowired
    private ApprovalProfileService approvalProfileService;
    @Autowired
    private ApprovalClosedEventHandler approvalClosedEventHandler;
    @Autowired
    private ApprovalRequestedEventHandler approvalRequestedEventHandler;

    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private DiscoveryFinishedEventHandler discoveryFinishedEventHandler;

    @Autowired
    private ScheduledJobsRepository scheduledJobsRepository;
    @Autowired
    private ScheduledJobFinishedEventHandler scheduledJobFinishedEventHandler;

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private SettingService settingService;
    @Autowired
    private NotificationListener notificationListener;
    @Autowired
    private NotificationProfileService notificationProfileService;
    @Autowired
    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;

    @Autowired
    private TriggerRepository triggerRepository;
    @Autowired
    private ActionRepository actionRepository;
    @Autowired
    private ExecutionRepository executionRepository;
    @Autowired
    private ExecutionItemRepository executionItemRepository;

    private WireMockServer mockServer;

    @Test
    void testCertificateStatusChangedAndApprovalEvents() throws EventException, NotFoundException, AlreadyExistException {
        Group group = new Group();
        group.setName("TestGroup");
        group.setEmail("grouptest@example.com");
        group = groupRepository.save(group);

        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        final Certificate certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCercertificatetificate");
        certificate.setSerialNumber("123456789");
        certificate.setNotBefore(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        certificate.setNotAfter(Date.from(Instant.now().plus(100, ChronoUnit.DAYS)));
        certificate.setCertificateType(CertificateType.X509);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.INACTIVE);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificateRepository.save(certificate);

        associationService.setGroups(Resource.CERTIFICATE, certificate.getUuid(), Set.of(group.getUuid()));

        certificateService.validate(certificate);
        certificateStatusChangedEventHandler.handleEvent(CertificateStatusChangedEventHandler.constructEventMessage(certificate.getUuid(), CertificateValidationStatus.INACTIVE, certificate.getValidationStatus()));
        List<CertificateEventHistoryDto> historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(1, historyList.size());
        Assertions.assertEquals(CertificateEvent.UPDATE_VALIDATION_STATUS, historyList.getFirst().getEvent());

        ApprovalProfileRequestDto approvalProfileRequestDto = new ApprovalProfileRequestDto();
        approvalProfileRequestDto.setName("TestApprovalProfile");
        approvalProfileRequestDto.setExpiry(24);
        approvalProfileRequestDto.setEnabled(true);

        ApprovalStepRequestDto approvalStepRequestDto = new ApprovalStepRequestDto();
        approvalStepRequestDto.setRoleUuid(UUID.randomUUID());
        approvalStepRequestDto.setRequiredApprovals(1);
        approvalStepRequestDto.setOrder(1);
        approvalProfileRequestDto.getApprovalSteps().add(approvalStepRequestDto);
        ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);

        Approval approval = new Approval();
        approval.setApprovalProfileVersionUuid(approvalProfile.getTheLatestApprovalProfileVersion().getUuid());
        approval.setStatus(ApprovalStatusEnum.PENDING);
        approval.setAction(ResourceAction.REVOKE);
        approval.setResource(Resource.CERTIFICATE);
        approval.setObjectUuid(certificate.getUuid());
        approval.setCreatorUuid(UUID.randomUUID());
        approval.setCreatedAt(new Date());
        approval.setExpiryAt(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        approval = approvalRepository.save(approval);

        ApprovalStepDto approvalStepDto = approvalProfile.getTheLatestApprovalProfileVersion().getApprovalSteps().getFirst().mapToDto();
        approvalRequestedEventHandler.handleEvent(ApprovalRequestedEventHandler.constructEventMessage(approval.getUuid(), approvalStepDto));
        historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(2, historyList.size());
        Assertions.assertEquals(CertificateEvent.APPROVAL_REQUEST, historyList.getFirst().getEvent());

        Assertions.assertDoesNotThrow(() -> certificateActionPerformedEventHandler.handleEvent(CertificateActionPerformedEventHandler.constructEventMessage(certificate.getUuid(), ResourceAction.REVOKE)));

        approvalClosedEventHandler.handleEvent(ApprovalClosedEventHandler.constructEventMessage(approval.getUuid()));
        historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(3, historyList.size());
        Assertions.assertEquals(CertificateEvent.APPROVAL_CLOSE, historyList.getFirst().getEvent());
    }

    @Test
    void testDiscoveryFinishedEvent() throws EventException {
        DiscoveryHistory discovery = new DiscoveryHistory();
        discovery.setName("TestDiscovery");
        discovery.setStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorUuid(UUID.randomUUID());
        discovery.setConnectorStatus(DiscoveryStatus.COMPLETED);
        discovery = discoveryRepository.save(discovery);

        discoveryFinishedEventHandler.handleEvent(DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), null,null, new DiscoveryResult(DiscoveryStatus.COMPLETED, "Test")));
        discovery = discoveryRepository.findByUuid(discovery.getUuid()).orElseThrow();
        Assertions.assertEquals(DiscoveryStatus.IN_PROGRESS, discovery.getStatus());

        discoveryFinishedEventHandler.handleEvent(DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), null,null, new DiscoveryResult(DiscoveryStatus.PROCESSING, "Test finalize")));
        discovery = discoveryRepository.findByUuid(discovery.getUuid()).orElseThrow();
        Assertions.assertEquals(DiscoveryStatus.COMPLETED, discovery.getStatus());
    }

    @Test
    void testScheduledJobFinishedEvent() {
        final ScheduledJob scheduledJob = new ScheduledJob();
        scheduledJob.setJobName("TestJob");
        scheduledJob.setCronExpression("0 0/3 * * * ? *");
        scheduledJob.setEnabled(true);
        scheduledJob.setSystem(false);
        scheduledJob.setOneTime(false);
        scheduledJob.setJobClassName(DiscoveryCertificateTask.class.getName());
        scheduledJobsRepository.save(scheduledJob);

        Assertions.assertDoesNotThrow(() -> scheduledJobFinishedEventHandler.handleEvent(ScheduledJobFinishedEventHandler.constructEventMessage(scheduledJob.getUuid(), new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, "Test"))));
    }

    @Test
    void testEventWithNotifications() throws NotFoundException, AlreadyExistException {
        mockServer = new WireMockServer(10001);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        UUID roleUuid = UUID.randomUUID();
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/notificationProvider/[^/]+/attributes/mapping")).willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/notificationProvider/notifications/[^/]+/notify")).willReturn(WireMock.ok()));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/roles/[^/]+")).willReturn(
                WireMock.okJson("""
                        {
                            "uuid": "%s",
                            "name": "TestRole",
                            "email": "testrole@example.com",
                            "systemRole": false
                        },
                        """.formatted(roleUuid.toString()))
        ));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/auth/roles/[^/]+/users")).willReturn(
                WireMock.okJson("""
                        [
                            {
                                "uuid": "%s",
                                "username": "TestUser1",
                                "email": "testuser1@example.com"
                            },
                            {
                                "uuid": "%s",
                                "username": "TestUser2",
                                "email": "testuser2@example.com"
                            }
                        ]
                        """.formatted(UUID.randomUUID(), UUID.randomUUID()))
        ));

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
        requestDto.setName("TestProfileDefault");
        requestDto.setRecipientType(RecipientType.DEFAULT);
        requestDto.setInternalNotification(true);
        requestDto.setNotificationInstanceUuid(instance.getUuid());
        NotificationProfileDetailDto notificationProfileDetailDto = notificationProfileService.createNotificationProfile(requestDto);

        requestDto.setName("TestProfileRole");
        requestDto.setRecipientType(RecipientType.ROLE);
        requestDto.setRecipientType(RecipientType.ROLE);
        requestDto.setRecipientUuids(List.of(roleUuid));
        NotificationProfileDetailDto notificationProfileDetailDto2 = notificationProfileService.createNotificationProfile(requestDto);

        List<UUID> notificationProfileUuids = List.of(UUID.fromString(notificationProfileDetailDto.getUuid()), UUID.fromString(notificationProfileDetailDto2.getUuid()));
        UUID triggerUuid = prepareTrigger(notificationProfileUuids);

        EventSettingsDto eventSettingsDto = new EventSettingsDto();
        eventSettingsDto.setEvent(ResourceEvent.CERTIFICATE_STATUS_CHANGED);
        eventSettingsDto.setTriggerUuids(List.of(triggerUuid));
        settingService.updateEventSettings(eventSettingsDto);

        Group group = new Group();
        group.setName("TestGroup");
        group.setEmail("grouptest@example.com");
        group = groupRepository.save(group);

        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        final Certificate certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCercertificatetificate");
        certificate.setSerialNumber("123456789");
        certificate.setNotBefore(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        certificate.setNotAfter(Date.from(Instant.now().plus(100, ChronoUnit.DAYS)));
        certificate.setCertificateType(CertificateType.X509);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.INACTIVE);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificateRepository.save(certificate);

        associationService.setGroups(Resource.CERTIFICATE, certificate.getUuid(), Set.of(group.getUuid()));

        CertificateStatusChangedEventData eventData = new CertificateStatusChangedEventData();
        eventData.setOldStatus(CertificateValidationStatus.INACTIVE.getLabel());
        eventData.setNewStatus(CertificateValidationStatus.VALID.getLabel());
        eventData.setCertificateUuid(certificate.getUuid());
        eventData.setFingerprint(certificate.getFingerprint());
        eventData.setSerialNumber(certificate.getSerialNumber());
        eventData.setSubjectDn(certificate.getSubjectDn());
        eventData.setIssuerDn(certificate.getIssuerDn());
        eventData.setNotBefore(certificate.getNotBefore().toInstant().atZone(ZoneId.systemDefault()));
        eventData.setExpiresAt(certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()));

        NotificationMessage notificationMessage = new NotificationMessage(ResourceEvent.CERTIFICATE_STATUS_CHANGED, Resource.CERTIFICATE, certificate.getUuid(), notificationProfileUuids, null, eventData);
        Assertions.assertDoesNotThrow(() -> notificationListener.processMessage(notificationMessage));

        mockServer.shutdown();
    }

    private UUID prepareTrigger(List<UUID> notificationProfileUuids) {
        Trigger trigger = new Trigger();
        trigger.setName("TestTrigger");
        trigger.setResource(Resource.CERTIFICATE);

        Execution execution = new Execution();
        execution.setName("TestExecution");
        execution.setResource(Resource.CERTIFICATE);
        execution.setType(ExecutionType.SEND_NOTIFICATION);
        executionRepository.save(execution);

        Set<ExecutionItem> executionItems = new HashSet<>();
        for(UUID notificationProfileUuid : notificationProfileUuids) {
            ExecutionItem executionItem = new ExecutionItem();
            executionItem.setNotificationProfileUuid(notificationProfileUuid);
            executionItem.setExecution(execution);
            executionItemRepository.save(executionItem);
            executionItems.add(executionItem);
        }
        execution.setItems(executionItems);

        Action action = new Action();
        action.setName("TestAction");
        action.setResource(Resource.CERTIFICATE);
        action.setExecutions(Set.of(execution));
        actionRepository.save(action);
        trigger.setActions(Set.of(action));
        trigger.setType(TriggerType.EVENT);
        trigger.setResource(Resource.CERTIFICATE);
        trigger.setEvent(ResourceEvent.CERTIFICATE_STATUS_CHANGED);
        trigger = triggerRepository.save(trigger);

        return  trigger.getUuid();
    }
}

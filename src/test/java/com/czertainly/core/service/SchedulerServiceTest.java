package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.properties.CustomAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryCertificateDto;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobDetailDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobHistoryResponseDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobsResponseDto;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.api.model.scheduler.UpdateScheduledJob;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.workflows.TriggerAssociationRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.events.data.DiscoveryResult;
import com.czertainly.core.events.handlers.CertificateDiscoveredEventHandler;
import com.czertainly.core.events.handlers.DiscoveryFinishedEventHandler;
import com.czertainly.core.messaging.listeners.EventListener;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.tasks.DiscoveryCertificateTask;
import com.czertainly.core.tasks.ScheduledJobInfo;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.UUID;

class SchedulerServiceTest extends BaseSpringBootTest {

    private static final int SCHEDULER_PORT = 8080;

    @Autowired
    private SchedulerService schedulerService;
    @Autowired
    private ScheduledJobsRepository scheduledJobsRepository;
    @Autowired
    private ScheduledJobHistoryRepository scheduledJobHistoryRepository;
    @Autowired
    private EventListener eventListener;

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private RuleService ruleService;
    @Autowired
    private ActionService actionService;
    @Autowired
    private TriggerService triggerService;
    @Autowired
    private TriggerAssociationRepository triggerAssociationRepository;

    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateEventHistoryRepository certificateEventHistoryRepository;

    @Autowired
    private DiscoveryService discoveryService;
    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    @Test
    void runScheduledDiscoveryWithTriggers() throws AlreadyExistException, NotFoundException, AttributeException, SchedulerException, CertificateException, IOException, EventException {
        // register custom attribute
        CustomAttribute certificateDomainAttr = new CustomAttribute();
        certificateDomainAttr.setUuid(UUID.randomUUID().toString());
        certificateDomainAttr.setName("domain");
        certificateDomainAttr.setType(AttributeType.CUSTOM);
        certificateDomainAttr.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties customProps = new CustomAttributeProperties();
        customProps.setLabel("Domain of certificate");
        certificateDomainAttr.setProperties(customProps);
        attributeEngine.updateCustomAttributeDefinition(certificateDomainAttr, List.of(Resource.CERTIFICATE));

        // create conditions
        ConditionItemRequestDto conditionItemRequest = new ConditionItemRequestDto();
        conditionItemRequest.setFieldSource(FilterFieldSource.PROPERTY);
        conditionItemRequest.setFieldIdentifier(FilterField.COMMON_NAME.name());
        conditionItemRequest.setOperator(FilterConditionOperator.ENDS_WITH);
        conditionItemRequest.setValue(".cz");

        ConditionRequestDto conditionRequest = new ConditionRequestDto();
        conditionRequest.setName("CommonNameEndsCZCondition");
        conditionRequest.setResource(Resource.CERTIFICATE);
        conditionRequest.setType(ConditionType.CHECK_FIELD);
        conditionRequest.setItems(List.of(conditionItemRequest));
        ConditionDto condition = ruleService.createCondition(conditionRequest);

        // create ignore condition
        conditionItemRequest.setValue(".com");
        conditionRequest.setName("CommonNameEndsCOMCondition");
        ConditionDto conditionIgnore = ruleService.createCondition(conditionRequest);

        // create rule
        RuleRequestDto ruleRequest = new RuleRequestDto();
        ruleRequest.setName("CommonNameEndsCZRule");
        ruleRequest.setResource(Resource.CERTIFICATE);
        ruleRequest.setConditionsUuids(List.of(condition.getUuid()));
        RuleDetailDto rule = ruleService.createRule(ruleRequest);

        // create ignore rule
        ruleRequest.setName("CommonNameEndsCOMRule");
        ruleRequest.setConditionsUuids(List.of(conditionIgnore.getUuid()));
        RuleDetailDto ruleIgnore = ruleService.createRule(ruleRequest);

        // create execution
        ExecutionItemRequestDto executionItemRequest = new ExecutionItemRequestDto();
        executionItemRequest.setFieldSource(FilterFieldSource.CUSTOM);
        executionItemRequest.setFieldIdentifier("%s|%s".formatted(certificateDomainAttr.getName(), certificateDomainAttr.getContentType().name()));
        executionItemRequest.setData("CZ");

        ExecutionRequestDto executionRequest = new ExecutionRequestDto();
        executionRequest.setName("CategorizeCertificatesExecution");
        executionRequest.setResource(Resource.CERTIFICATE);
        executionRequest.setType(ExecutionType.SET_FIELD);
        executionRequest.setItems(List.of(executionItemRequest));
        ExecutionDto execution = actionService.createExecution(executionRequest);

        // create action
        ActionRequestDto actionRequest = new ActionRequestDto();
        actionRequest.setName("CategorizeCertificatesAction");
        actionRequest.setResource(Resource.CERTIFICATE);
        actionRequest.setExecutionsUuids(List.of(execution.getUuid()));
        ActionDetailDto action = actionService.createAction(actionRequest);

        // create trigger
        TriggerRequestDto triggerRequest = new TriggerRequestDto();
        triggerRequest.setName("DiscoveryCertificatesCategorization");
        triggerRequest.setType(TriggerType.EVENT);
        triggerRequest.setEvent(ResourceEvent.CERTIFICATE_DISCOVERED);
        triggerRequest.setResource(Resource.CERTIFICATE);
        triggerRequest.setRulesUuids(List.of(rule.getUuid()));
        triggerRequest.setActionsUuids(List.of(action.getUuid()));
        TriggerDetailDto trigger = triggerService.createTrigger(triggerRequest);

        // create ignore trigger
        triggerRequest.setName("DiscoveryCertificatesCategorizationIgnore");
        triggerRequest.setRulesUuids(List.of(ruleIgnore.getUuid()));
        triggerRequest.setIgnoreTrigger(true);
        triggerRequest.setActionsUuids(List.of());
        TriggerDetailDto triggerIgnore = triggerService.createTrigger(triggerRequest);

        WireMockServer mockServer = new WireMockServer(0);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        // create connector
        Connector connector = new Connector();
        connector.setName("discoveryProviderConnector");
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.DISCOVERY_PROVIDER);
        functionGroup.setName(FunctionGroupCode.DISCOVERY_PROVIDER.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of("IpAndPort")));
        connector2FunctionGroupRepository.save(c2fg);

        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        // create scheduled discovery job
        String jobName = "TestScheduledDiscoveryWithTriggers";
        DiscoveryDto discoveryDto = new DiscoveryDto();
        discoveryDto.setName("TestCzertainlyDiscoveryWithTriggers");
        discoveryDto.setKind("IP-Hostname");
        discoveryDto.setConnectorUuid(connector.getUuid().toString());
        discoveryDto.setAttributes(List.of());
        discoveryDto.setTriggers(List.of(UUID.fromString(triggerIgnore.getUuid()), UUID.fromString(trigger.getUuid())));

        ScheduledJob scheduledJobEntity = new ScheduledJob();
        scheduledJobEntity.setJobName(jobName);
        scheduledJobEntity.setCronExpression("0 0/3 * * * ? *");
        scheduledJobEntity.setObjectData(discoveryDto);
        scheduledJobEntity.setEnabled(true);
        scheduledJobEntity.setJobClassName(DiscoveryCertificateTask.class.getName());

        scheduledJobEntity = scheduledJobsRepository.save(scheduledJobEntity);

        String discoveredCertificatesMockResponse = """
                {
                    "uuid": "4bd64640-be29-4e14-aad8-5c0ffa55c5bd",
                    "status": "completed",
                    "totalCertificatesDiscovered": 2,
                    "certificateData": [
                        {
                            "uuid": "0279d416-02ed-4415-a8cd-85af3f083222",
                            "base64Content": "MIIDyjCCArKgAwIBAgIUULw4BO/gvFzW2wMYXRhmz1kPPdAwDQYJKoZIhvcNAQELBQAwZDEUMBIGA1UEAwwLdGVzdGNlcnQuY3oxCzAJBgNVBAYTAkNaMRgwFgYDVQQIDA9DZW50cmFsIEJvaGVtaWExDzANBgNVBAcMBlNsYW7DvTEUMBIGA1UECgwLM0tleUNvbXBhbnkwHhcNMjQxMDIxMTAzMDEyWhcNMjUxMDIxMTAzMDEyWjBkMRQwEgYDVQQDDAt0ZXN0Y2VydC5jejELMAkGA1UEBhMCQ1oxGDAWBgNVBAgMD0NlbnRyYWwgQm9oZW1pYTEPMA0GA1UEBwwGU2xhbsO9MRQwEgYDVQQKDAszS2V5Q29tcGFueTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJ112/a4p9sZ4F2fABLGtSBrbp71n/0uG+H/3usEQU8/FIW644ly5hNl8+SloPWryCCxOl+saXTKv62h0HnE/HNFMKlps4wwWNMsTploFKiAW9AbaDtzNrMy9f/orMoZldDZt5dLX8UR3qMmdK8nlqiJOyCAxIS70OsEQC8fGuIMNYeW6eidXGHjvpqApWnGTyA4U1bJWsDWcOIh/LL2ae9nwTJjVrHthrM6Wq6PplaPxEKYABp51UAQLMzY+cJElcKmwQxiK+zOHns7/ocosZVqI2QyxSmG60icabyrIT6HQHKVNzZHkltmduyYun9YZ+nl68YOuNmtSNi1TLMlfGECAwEAAaN0MHIwHQYDVR0OBBYEFOWFJRXdCer5Bpj+9JrquuJ7e5eQMB8GA1UdIwQYMBaAFOWFJRXdCer5Bpj+9JrquuJ7e5eQMA4GA1UdDwEB/wQEAwIFoDAgBgNVHSUBAf8EFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDQYJKoZIhvcNAQELBQADggEBAA6AWaBFDAWL8oSBCP3q1s2Gq9QhR2QEBZ5tPOMTN5GpIzXxXdm4nHHBK/pSFABUNmrwQMapvq/y6IZ7hNMdC89MTOsHLD0EVPmHHO4xhzMG08XpJdevTrvktjpt0+ju81ratLg34pvJLeLF7ZL5AxwOl6qKX6RgwHpdBUipAYeeVhTVtQ7FLvakKDwYLiN6YFXuM1+CDAK3fsJ6sZki3uRvLYsUi7bguIQCmCQ0/n+T62Driq6mh1FkFB3sgpSFjfEo3bEaaHzF1YZr6otTYPNzcLCStJ5SYNBXKbw7YKAcYavL6yMNTQ2CjmLVnwjjd3O/Sv1kEhZMu86mHeNZK0I=",
                            "meta": []
                        },
                        {
                            "uuid": "ea119f0f-80fb-4d51-aa43-a049f9794a80",
                            "base64Content": "MIIDzDCCArSgAwIBAgIUZIyXNStxHEmQiOtuYAj7C7fJ9NQwDQYJKoZIhvcNAQELBQAwZTEVMBMGA1UEAwwMdGVzdGNlcnQuY29tMQswCQYDVQQGEwJDWjEYMBYGA1UECAwPQ2VudHJhbCBCb2hlbWlhMQ8wDQYDVQQHDAZTbGFuw70xFDASBgNVBAoMCzNLZXlDb21wYW55MB4XDTI0MTAyMTEwMzUwOFoXDTI1MTAyMTEwMzUwOFowZTEVMBMGA1UEAwwMdGVzdGNlcnQuY29tMQswCQYDVQQGEwJDWjEYMBYGA1UECAwPQ2VudHJhbCBCb2hlbWlhMQ8wDQYDVQQHDAZTbGFuw70xFDASBgNVBAoMCzNLZXlDb21wYW55MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2CDSXCL/6oE6jOHAxmPMwPSz11P4A4On0R2vo4ff6828lztprQZfdZFpDHiTMi8KRmZWCLtvbwO9inrIB0Ucs0psOuDOOuQdBe6PxmED2jG0NZFXk6N2oz4Ii8HjzDdvkNuPPDSPRrNoHZj4AGp00e5Eap5BXjEyWfp+Q/YO8Jxe96VFRtjZAV2u0/ooX1iu79E9Kuy59dyddGUTP0NCFML21VNp+G2LSRdDjlXPAjpftZ/f2l/1/6V55HI78R2fH801fEgwsPfB60k/LGX5O4f7erbZmTTUxCAq2LUZ4jjbJmuxqI9ExJkI0Oj5f1del6/216VVGZzB3OsaniatfQIDAQABo3QwcjAdBgNVHQ4EFgQUWLvLB6z1rgd0blgV8FPXHYXJQqgwHwYDVR0jBBgwFoAUWLvLB6z1rgd0blgV8FPXHYXJQqgwDgYDVR0PAQH/BAQDAgWgMCAGA1UdJQEB/wQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjANBgkqhkiG9w0BAQsFAAOCAQEADYJZwsU1ucUOvq45aGZ4T9/4/H8Cy+HE761j+9CB6IV2ARnM5xG9CLSKRaDkzHwwbtREJljCcg1b8ZzjmGwjwBqvHDesWw87oz/6w2CdqEoILnUxkYoLLQ6wRtEKSUZEUzeEwqeVVcvo6TrsVz3dPkoeHubkEhhdaNyOjtbQs2F3JMrjhXsu2vXJ4D7ugFAsMx4w2LsxYLuAeG/njXseca80G+0f8NqFz+q4WpjxNdSY1Z4FrP2OGkVpSjFzJEWwMsdXSKB3QjaL1XW7QSjgefVXA+NPpgzXlFvxo+c4SnoCZ3QwcQIQ+QEEwCwA9Xvw96cFheLinFmLLsuobsNEHQ==",
                            "meta": []
                        },
                        {
                            "uuid": "dd601efe-0252-4b65-bd68-967201122dc8",
                            "base64Content": "MIIDZTCCAkygAwIBAgIBADANBgkqhkiG9w0BAQ0FADBMMQswCQYDVQQGEwJzazERMA8GA1UECAwIU2xvdmFraWExFDASBgNVBAoMC1Rlc3RDb21wYW55MRQwEgYDVQQDDAt0ZXN0Y2VydC5zazAeFw0yNTA1MDgxMjQ1MjZaFw0yNjA1MDgxMjQ1MjZaMEwxCzAJBgNVBAYTAnNrMREwDwYDVQQIDAhTbG92YWtpYTEUMBIGA1UECgwLVGVzdENvbXBhbnkxFDASBgNVBAMMC3Rlc3RjZXJ0LnNrMIIBIzANBgkqhkiG9w0BAQEFAAOCARAAMIIBCwKCAQIArQGMK3T2SgsCI+zxOkUK2FPAr/RArYb9PDn7SEa9j+HvQIMSIo3wHnI5X/jUwt113uy3QIyvvE4mLVc8IM+IPxXSrlGdrCMgjbPKsS+Byz1hixP7q5pMPl+GW7IlqCCV4HKxpVwIPOHc8kDKj45kZlG/kg1cjYjIcut0nMwE8yYkQJJt3fDmV+MMrGdFwTcpD+FMxwYLwtTfNXyjZmjbVjsztdP5UmVpnVMJEBQK3ghyev1jjN9loGuvebUIuYLPkN+ppZKpdZTMo+KTZI0G43GTK9nb4eAz9SATk4aea64yAIlO8CLkGOzCG+rhABSFQGJQNfqjqdg5N/+oUq9ETC8CAwEAAaNQME4wHQYDVR0OBBYEFEJImX9Y09Nf4y4RbodubMU8qb/NMB8GA1UdIwQYMBaAFEJImX9Y09Nf4y4RbodubMU8qb/NMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQENBQADggECAE+HNIPiaFGIwNrCxqkeRIlyNPfcG3sQPbcdFZyvGtmvRO62IoXq3YLRMQh9eMQ6fPyR/3Tf5EcQE/8RMiC1Of4hLeCHiptHYg3QslP0Cm15UGDTSg1A0ehG/KvINSfwxJr3r62MmaulgLE+1rvatyAsEDVkxdTGdKvsE8eTu8jS7KYaV27/rKmzDgB8VKbfkHBRUVEgsbbaUVdsM0vDVUMNZIn3voWnaq7zE+dwvSLds/95L/cGvhfEH6Vq5k5piBp3Nwpj8X0JABCmyk9ihbmecIhNeY/tWA/cr3sPwC+Gy2ezPbTO0siEEjAleD4DjP3NfGjfSNJnzOGcoBMRxUPN",
                            "meta": []
                        }
                    ],
                    "meta": []
                }
                """;
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/discoveryProvider/[^/]+/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/discoveryProvider/[^/]+/attributes/validate"))
                .willReturn(WireMock.okJson("true")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/discoveryProvider/discover"))
                .willReturn(WireMock.okJson(discoveredCertificatesMockResponse)));

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/discoveryProvider/discover/4bd64640-be29-4e14-aad8-5c0ffa55c5bd"))
                .willReturn(WireMock.okJson(discoveredCertificatesMockResponse)));

        schedulerService.runScheduledJob(jobName);


        List<DiscoveryHistory> discoveries = discoveryRepository.findAll();
        Assertions.assertEquals(1, discoveries.size());

        // simulate events
        DiscoveryHistory discovery = discoveries.getFirst();
        EventMessage eventMessage = CertificateDiscoveredEventHandler.constructEventMessage(discovery.getUuid(), null, new ScheduledJobInfo(scheduledJobEntity.getJobName(), scheduledJobEntity.getUuid(), scheduledJobHistoryRepository.findAll().getFirst().getUuid()));
        eventListener.processMessage(eventMessage);
        eventMessage = DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), null, eventMessage.getScheduledJobInfo(), new DiscoveryResult(DiscoveryStatus.PROCESSING, null));
        eventListener.processMessage(eventMessage);

        discovery = discoveryRepository.findByUuid(discovery.getUuid()).orElseThrow();
        Assertions.assertEquals(DiscoveryStatus.COMPLETED, discovery.getStatus());

        ScheduledJobHistory jobHistory = scheduledJobHistoryRepository.findTopByScheduledJobUuidOrderByJobExecutionDesc(scheduledJobEntity.getUuid());
        Assertions.assertNotNull(jobHistory);

        ScheduledJobHistoryResponseDto jobHistoryResponse = schedulerService.getScheduledJobHistory(SecurityFilter.create(), new PaginationRequestDto(), scheduledJobEntity.getUuid().toString());

        Assertions.assertEquals(1, jobHistoryResponse.getScheduledJobHistory().size());
        Assertions.assertEquals(SchedulerJobExecutionStatus.SUCCESS, jobHistoryResponse.getScheduledJobHistory().getFirst().getStatus());

        // assert triggers evaluation
        TriggerHistorySummaryDto triggerSummary = triggerService.getTriggerHistorySummary(discovery.getUuid().toString());
        Assertions.assertEquals(3, triggerSummary.getObjectsEvaluated());
        Assertions.assertEquals(2, triggerSummary.getObjectsMatched());
        Assertions.assertEquals(1, triggerSummary.getObjectsIgnored());

        DiscoveryCertificateResponseDto discoveredCertificates = discoveryService.getDiscoveryCertificates(discovery.getSecuredUuid(), null, 10, 1);
        Assertions.assertEquals(3, discoveredCertificates.getCertificates().size());

        boolean matched = false;
        for (DiscoveryCertificateDto discoveryCertificate : discoveredCertificates.getCertificates()) {
            if (discoveryCertificate.getCommonName().endsWith(".cz")) {
                CertificateDetailDto certificateDetailDto = certificateService.getCertificate(SecuredUUID.fromString(discoveryCertificate.getInventoryUuid()));

                matched = true;
                Assertions.assertEquals(1, certificateDetailDto.getCustomAttributes().size());
                Assertions.assertEquals("CZ", certificateDetailDto.getCustomAttributes().getFirst().getContent().getFirst().getData());
            }
        }
        Assertions.assertTrue(matched);
    }

    @Test
    void testRegisterScheduledJobAndOperations() throws SchedulerException, NotFoundException {
        final String jobName = "TestDiscoveryScheduled";
        final String cronExpressionUpdate = "0 0/30 * * * ? *";

        WireMockServer mockServer = new WireMockServer(SCHEDULER_PORT);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());

        mockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/scheduler/create")).willReturn(
                WireMock.ok()));
        mockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/scheduler/update")).willReturn(
                WireMock.ok()));
        mockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/scheduler/" + jobName)).willReturn(
                WireMock.noContent()));

        ScheduledJobDetailDto jobDetailDto = schedulerService.registerScheduledJob(DiscoveryCertificateTask.class,jobName, "0 0/3 * * * ? *", true, null);

        UpdateScheduledJob updateScheduledJob = new UpdateScheduledJob();
        updateScheduledJob.setCronExpression(cronExpressionUpdate);
        schedulerService.updateScheduledJob(jobDetailDto.getUuid().toString(), updateScheduledJob);

        jobDetailDto = schedulerService.getScheduledJobDetail(jobDetailDto.getUuid().toString());
        Assertions.assertEquals(jobName, jobDetailDto.getJobName());
        Assertions.assertEquals(cronExpressionUpdate, jobDetailDto.getCronExpression());

        schedulerService.deleteScheduledJob(jobDetailDto.getUuid().toString());

        ScheduledJobsResponseDto listResponse = schedulerService.listScheduledJobs(SecurityFilter.create(), new PaginationRequestDto());
        Assertions.assertEquals(0, listResponse.getTotalItems());

        mockServer.shutdown();
    }

}

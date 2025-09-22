package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.compliance.v2.*;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.connector.compliance.v2.ComplianceRuleRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateSubjectType;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceRuleAvailabilityStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileListDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceRuleListDto;
import com.czertainly.api.model.core.compliance.v2.ProviderComplianceRulesDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.v2.ComplianceProfileService;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

class ComplianceProfileServiceV2Test extends BaseSpringBootTest {

    private static final String KIND_V1 = "kindV1";
    private static final String KIND_V2 = "kindV2";

    @Autowired
    private ComplianceProfileRepository complianceProfileRepository;

    @Autowired
    private ComplianceProfileService complianceProfileService;

    @Autowired
    private ComplianceProfileRuleRepository complianceProfileRuleRepository;

    @Autowired
    private ComplianceProfileAssociationRepository complianceProfileAssociationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;

    @Autowired
    private ComplianceInternalRuleRepository internalRuleRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    private Connector connectorV1;
    private Connector connectorV2;
    private WireMockServer mockServer;
    private ComplianceProfile complianceProfile;

    private UUID associatedRaProfileUuid;
    private UUID unassociatedRaProfileUuid;


    private UUID internalRuleUuid;
    private UUID internalRule2Uuid;
    private final UUID complianceV1RuleUuid = UUID.randomUUID();
    private final UUID complianceV1Rule2Uuid = UUID.randomUUID();
    private final UUID complianceV1GroupUuid = UUID.randomUUID();
    private final UUID complianceV2RuleUuid = UUID.randomUUID();
    private final UUID complianceV2Rule2Uuid = UUID.randomUUID();
    private final UUID complianceV2GroupUuid = UUID.randomUUID();
    private final UUID complianceV2Group2Uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() throws NotFoundException, AlreadyExistException {
        mockServer = new WireMockServer(0);
        mockServer.start();

        mockComplianceProviderResponses(true);
        mockComplianceProviderV1Responses();

        connectorV1 = createConnector("TestConnectorV1", FunctionGroupCode.COMPLIANCE_PROVIDER);
        connectorV2 = createConnector("TestConnectorV2", FunctionGroupCode.COMPLIANCE_PROVIDER_V2);

        complianceProfile = new ComplianceProfile();
        complianceProfile.setName("TestProfile");
        complianceProfile.setDescription("Sample Description");
        complianceProfileRepository.save(complianceProfile);

        createInternalRule();
        createComplianceProfileAssociations();

        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connectorV2);
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        RaProfile raProfile = new RaProfile();
        raProfile.setName("TestProfile");
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile = raProfileRepository.save(raProfile);
        associatedRaProfileUuid = raProfile.getUuid();

        ComplianceProfileAssociation complianceProfileAssociation = new ComplianceProfileAssociation();
        complianceProfileAssociation.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileAssociation.setResource(Resource.RA_PROFILE);
        complianceProfileAssociation.setObjectUuid(raProfile.getUuid());
        complianceProfileAssociationRepository.save(complianceProfileAssociation);

        raProfile = new RaProfile();
        raProfile.setName("TestProfile2");
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile = raProfileRepository.save(raProfile);
        unassociatedRaProfileUuid = raProfile.getUuid();

        complianceProfile = complianceProfileRepository.findWithAssociationsByUuid(complianceProfile.getUuid()).orElseThrow();
        Assertions.assertFalse(complianceProfile.getComplianceRules().isEmpty(), "Compliance rules should be loaded");
        Assertions.assertFalse(complianceProfile.getAssociations().isEmpty(), "Compliance associations should be loaded");
    }

    private Connector createConnector(String name, FunctionGroupCode functionGroupCode) {
        Connector connector = new Connector();
        connector.setName(name);
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(functionGroupCode);
        functionGroup.setName(functionGroupCode.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setConnectorUuid(connector.getUuid());
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setFunctionGroupUuid(functionGroup.getUuid());
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of(functionGroupCode == FunctionGroupCode.COMPLIANCE_PROVIDER ? KIND_V1 : KIND_V2)));
        connector2FunctionGroupRepository.save(c2fg);
        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        return connector;
    }

    private void createInternalRule() throws AlreadyExistException {
        ConditionItemRequestDto conditionItemRequestDto = new ConditionItemRequestDto();
        conditionItemRequestDto.setFieldSource(FilterFieldSource.PROPERTY);
        conditionItemRequestDto.setFieldIdentifier("KEY_SIZE");
        conditionItemRequestDto.setOperator(FilterConditionOperator.EQUALS);
        conditionItemRequestDto.setValue(1024);

        ComplianceInternalRuleRequestDto ruleRequestDto = new ComplianceInternalRuleRequestDto();
        ruleRequestDto.setName("TestInternalRule");
        ruleRequestDto.setResource(Resource.CERTIFICATE);
        ruleRequestDto.setConditionItems(List.of(conditionItemRequestDto));
        ComplianceRuleListDto ruleDetailDto = complianceProfileService.createComplianceInternalRule(ruleRequestDto);
        internalRuleUuid = ruleDetailDto.getUuid();

        conditionItemRequestDto.setFieldIdentifier("PRIVATE_KEY");
        conditionItemRequestDto.setValue(true);
        ruleRequestDto.setName("TestInternalRule2");
        ruleDetailDto = complianceProfileService.createComplianceInternalRule(ruleRequestDto);
        internalRule2Uuid = ruleDetailDto.getUuid();
    }

    private void createComplianceProfileAssociations() {
        var complianceProfileRule = new ComplianceProfileRule();
        complianceProfileRule.setComplianceProfile(complianceProfile);
        complianceProfileRule.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileRule.setResource(Resource.CERTIFICATE);
        complianceProfileRule.setConnectorUuid(connectorV2.getUuid());
        complianceProfileRule.setKind(KIND_V2);
        complianceProfileRule.setComplianceRuleUuid(complianceV2RuleUuid);
        complianceProfileRuleRepository.save(complianceProfileRule);

        ComplianceProfileRule complianceProfileRule2 = new ComplianceProfileRule();
        complianceProfileRule2.setComplianceProfile(complianceProfile);
        complianceProfileRule2.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileRule2.setResource(Resource.CERTIFICATE);
        complianceProfileRule2.setConnectorUuid(connectorV2.getUuid());
        complianceProfileRule2.setKind(KIND_V2);
        complianceProfileRule2.setComplianceGroupUuid(complianceV2GroupUuid);
        complianceProfileRuleRepository.save(complianceProfileRule2);

        ComplianceProfileRule complianceProfileRule3 = new ComplianceProfileRule();
        complianceProfileRule3.setComplianceProfile(complianceProfile);
        complianceProfileRule3.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileRule3.setResource(Resource.CERTIFICATE);
        complianceProfileRule3.setInternalRuleUuid(internalRuleUuid);
        complianceProfileRuleRepository.save(complianceProfileRule3);
    }

    private void mockComplianceProviderResponses(boolean defaultResponses) {
        String complianceRuleResponse = """
                {
                  "uuid": "%s",
                  "name": "Rule1",
                  "description": "Description",
                  "resource": "certificates",
                  "type": "X.509"
                }
                """.formatted(complianceV2RuleUuid);

        String complianceRule2Response = """
                {
                  "uuid": "%s",
                  "name": "Rule2",
                  "description": "Description2",
                  "groupUuid": "%s",
                  "resource": "certificates",
                  "type": "%s",
                  "attributes": []
                }
                """.formatted(complianceV2Rule2Uuid, complianceV2Group2Uuid, defaultResponses ? CertificateType.X509.getCode() : CertificateType.SSH.getCode());

        String complianceGroupResponse = """
                {
                  "uuid": "%s",
                  "name": "Group1",
                  "description": "Sample description"
                }
                """.formatted(complianceV2GroupUuid);

        String complianceGroup2Response = """
                {
                  "uuid": "%s",
                  "name": "Group2",
                  "description": "Sample description",
                  "resource": "%s"
                }
                """.formatted(complianceV2Group2Uuid, defaultResponses ? Resource.CERTIFICATE.getCode() : Resource.CRYPTOGRAPHIC_KEY.getCode());

        WireMock.configureFor("localhost", mockServer.port());

        WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v2/complianceProvider/%s/rules/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(new NotFoundException("Rule", "<UUID>").getMessage())
                        .withStatus(404)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                defaultResponses ?
                                        """
                                                [
                                                  %s,
                                                  %s
                                                ]
                                                """.formatted(complianceRuleResponse, complianceRule2Response)
                                        :
                                        """
                                                [
                                                  %s
                                                ]
                                                """.formatted(complianceRule2Response))
                        .withStatus(200)));

        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                defaultResponses ?
                                        """
                                                {
                                                  "rules": [
                                                    %s,
                                                    %s
                                                  ],
                                                  "groups": [
                                                    %s,
                                                    %s
                                                  ]
                                                }
                                                """.formatted(complianceRuleResponse, complianceRule2Response, complianceGroupResponse, complianceGroup2Response)
                                        :
                                        """
                                                {
                                                  "rules": [
                                                    %s
                                                  ],
                                                  "groups": [
                                                    %s
                                                  ]
                                                }
                                                """.formatted(complianceRule2Response, complianceGroup2Response))
                        .withStatus(200)));

        if (defaultResponses) {
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules/%s".formatted(KIND_V2, complianceV2RuleUuid)))
                    .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(complianceRuleResponse)
                            .withStatus(200)));
        } else {
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules/%s".formatted(KIND_V2, complianceV2RuleUuid)))
                    .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(new NotFoundException("Rule", "<UUID>").getMessage())
                            .withStatus(404)));
        }

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules/%s".formatted(KIND_V2, complianceV2Rule2Uuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceRule2Response)
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v2/complianceProvider/%s/groups/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(new NotFoundException("Group", "<UUID>").getMessage())
                        .withStatus(404)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  %s,
                                  %s
                                ]
                                """.formatted(complianceGroupResponse, complianceGroup2Response))
                        .withStatus(200)));

        if (defaultResponses) {
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s".formatted(KIND_V2, complianceV2GroupUuid)))
                    .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(complianceGroupResponse)
                            .withStatus(200)));
        } else {
            WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s".formatted(KIND_V2, complianceV2GroupUuid)))
                    .willReturn(WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(new NotFoundException("Group", "<UUID>").getMessage())
                            .withStatus(404)));
        }

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s".formatted(KIND_V2, complianceV2Group2Uuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceGroup2Response)
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s/rules".formatted(KIND_V2, complianceV2GroupUuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s/rules".formatted(KIND_V2, complianceV2Group2Uuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceGroup2Response)
                        .withStatus(200)));

    }

    private void mockComplianceProviderV1Responses() {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/complianceProvider/%s/rules".formatted(KIND_V1)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "uuid": "%s",
                                    "name": "Rule1-V1",
                                    "description": "Description",
                                    "certificateType": "X.509"
                                  },
                                  {
                                    "uuid": "%s",
                                    "name": "Rule2-V1",
                                    "description": "Description2",
                                    "groupUuid": "%s",
                                    "certificateType": "X.509",
                                    "attributes": []
                                  }
                                ]
                                """.formatted(complianceV1RuleUuid, complianceV1Rule2Uuid, complianceV1GroupUuid))
                        .withStatus(200)));
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/complianceProvider/%s/groups".formatted(KIND_V1)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "uuid": "%s",
                                    "name": "Group1-V1",
                                    "description": "Sample description"
                                  }
                                ]
                                """.formatted(complianceV1GroupUuid))
                        .withStatus(200)));
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testResourceObjectsHandling() throws NotFoundException {
        var complianceProfile2 = new ComplianceProfile();
        complianceProfile2.setName("TestProfile2");
        complianceProfile2.setDescription("Sample Description2");
        complianceProfileRepository.save(complianceProfile2);

        var objects = complianceProfileService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(2, objects.size());

        var profileInfo = complianceProfileService.getResourceObject(complianceProfile.getUuid());
        Assertions.assertEquals(complianceProfile.getName(), profileInfo.getName());
    }

    @Test
    void testListComplianceProfiles() {
        List<ComplianceProfileListDto> dtos = complianceProfileService.listComplianceProfiles(SecurityFilter.create());
        Assertions.assertNotNull(dtos);
        Assertions.assertEquals(1, dtos.size());
        Assertions.assertEquals(complianceProfile.getName(), dtos.getFirst().getName());
        Assertions.assertEquals(complianceProfile.getUuid(), dtos.getFirst().getUuid());
        Assertions.assertEquals(1, dtos.getFirst().getInternalRulesCount());
        Assertions.assertEquals(1, dtos.getFirst().getProviderRulesCount());
        Assertions.assertEquals(1, dtos.getFirst().getProviderGroupsCount());
    }

    @Test
    void testGetComplianceProfile() throws NotFoundException, ConnectorException {
        ComplianceProfileDto complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertNotNull(complianceProfileDto);
        Assertions.assertEquals(complianceProfileDto.getName(), complianceProfile.getName());
        Assertions.assertEquals(complianceProfileDto.getUuid(), complianceProfile.getUuid());
        Assertions.assertEquals(complianceProfileDto.getDescription(), complianceProfile.getDescription());
        Assertions.assertEquals(complianceProfileDto.getInternalRules().size(), complianceProfile.getComplianceRules().stream().filter(r -> r.getInternalRuleUuid() != null).count());
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(complianceProfileDto.getProviderRules().getFirst().getRules().size(), complianceProfile.getComplianceRules().stream().filter(r -> r.getComplianceRuleUuid() != null).count());
        Assertions.assertEquals(complianceProfileDto.getProviderRules().getFirst().getGroups().size(), complianceProfile.getComplianceRules().stream().filter(r -> r.getComplianceGroupUuid() != null).count());
    }

    @Test
    void testGetComplianceProfileNotFound() {
        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.getComplianceProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void createComplianceProfileTest() throws AlreadyExistException, AttributeException, NotFoundException, ConnectorException {
        ComplianceProfileRequestDto requestDto = new ComplianceProfileRequestDto();
        requestDto.setName("sample2");
        requestDto.setDescription("sampleDescription");

        ComplianceProfileDto dto = complianceProfileService.createComplianceProfile(requestDto);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(0, dto.getProviderRules().size());
        Assertions.assertEquals(0, dto.getInternalRules().size());
        Assertions.assertEquals("sample2", dto.getName());
        Assertions.assertEquals("sampleDescription", dto.getDescription());
    }

    @Test
    void createComplianceProfile_AlreadyExistsTest() {
        ComplianceProfileRequestDto requestDto = new ComplianceProfileRequestDto();
        requestDto.setName("TestProfile");
        requestDto.setDescription("description");

        Assertions.assertThrows(AlreadyExistException.class, () -> complianceProfileService.createComplianceProfile(requestDto));
    }

    @Test
    void updateComplianceProfileTest() throws AttributeException, NotFoundException, ConnectorException {
        ComplianceProfileUpdateRequestDto requestDto = new ComplianceProfileUpdateRequestDto();
        requestDto.setDescription("sampleDescription2");
        requestDto.setInternalRules(Set.of(internalRuleUuid, internalRule2Uuid));

        ComplianceRuleRequestDto ruleRequestV1Dto = new ComplianceRuleRequestDto();
        ruleRequestV1Dto.setUuid(complianceV1RuleUuid);
        ComplianceRuleRequestDto ruleRequestV1Dto2 = new ComplianceRuleRequestDto();
        ruleRequestV1Dto2.setUuid(complianceV1Rule2Uuid);
        ComplianceRuleRequestDto ruleRequestV2Dto = new ComplianceRuleRequestDto();
        ruleRequestV2Dto.setUuid(complianceV2RuleUuid);
        ComplianceRuleRequestDto ruleRequestV2Dto2 = new ComplianceRuleRequestDto();
        ruleRequestV2Dto2.setUuid(complianceV2Rule2Uuid);

        ProviderComplianceRulesRequestDto providerRequestV1Dto = new ProviderComplianceRulesRequestDto();
        providerRequestV1Dto.setConnectorUuid(connectorV1.getUuid());
        providerRequestV1Dto.setKind(KIND_V1);
        providerRequestV1Dto.setRules(Set.of(ruleRequestV1Dto, ruleRequestV1Dto2));
        providerRequestV1Dto.setGroups(Set.of(complianceV1GroupUuid));
        requestDto.getProviderRules().add(providerRequestV1Dto);

        ProviderComplianceRulesRequestDto providerRequestV2Dto = new ProviderComplianceRulesRequestDto();
        providerRequestV2Dto.setConnectorUuid(connectorV2.getUuid());
        providerRequestV2Dto.setKind(KIND_V2);
        providerRequestV2Dto.setRules(Set.of(ruleRequestV2Dto, ruleRequestV2Dto2));
        providerRequestV2Dto.setGroups(Set.of(complianceV2GroupUuid, complianceV2Group2Uuid));
        requestDto.getProviderRules().add(providerRequestV2Dto);

        ComplianceProfileDto dto = complianceProfileService.updateComplianceProfile(complianceProfile.getSecuredUuid(), requestDto);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("sampleDescription2", dto.getDescription());
        Assertions.assertEquals(2, dto.getInternalRules().size());
        Assertions.assertEquals(2, dto.getProviderRules().size());

        ProviderComplianceRulesDto providerRulesDtoV1 = dto.getProviderRules().stream().filter(p -> p.getKind().equals(KIND_V1)).findFirst().orElseThrow();
        ProviderComplianceRulesDto providerRulesDtoV2 = dto.getProviderRules().stream().filter(p -> p.getKind().equals(KIND_V2)).findFirst().orElseThrow();
        Assertions.assertEquals(2, providerRulesDtoV1.getRules().size());
        Assertions.assertEquals(1, providerRulesDtoV1.getGroups().size());
        Assertions.assertEquals(2, providerRulesDtoV2.getRules().size());
        Assertions.assertEquals(2, providerRulesDtoV2.getGroups().size());

        // test availability status, remove group and rule from the provider
        mockComplianceProviderResponses(false);

        dto = complianceProfileService.getComplianceProfile(complianceProfile.getSecuredUuid());
        Assertions.assertEquals(2, dto.getProviderRules().size());

        var providerRules = dto.getProviderRules().stream().filter(p -> p.getKind().equals(KIND_V2)).findFirst().orElseThrow();
        Assertions.assertEquals(2, providerRules.getRules().size());
        Assertions.assertEquals(2, providerRules.getGroups().size());
        Assertions.assertTrue(providerRules.getRules().stream().anyMatch(r -> r.getUuid().equals(complianceV2RuleUuid)));
        Assertions.assertEquals(ComplianceRuleAvailabilityStatus.NOT_AVAILABLE, providerRules.getRules().stream().filter(r -> r.getUuid().equals(complianceV2RuleUuid)).findFirst().orElseThrow().getAvailabilityStatus());
        Assertions.assertEquals(ComplianceRuleAvailabilityStatus.UPDATED, providerRules.getRules().stream().filter(r -> r.getUuid().equals(complianceV2Rule2Uuid)).findFirst().orElseThrow().getAvailabilityStatus());
        Assertions.assertTrue(providerRules.getGroups().stream().anyMatch(r -> r.getUuid().equals(complianceV2GroupUuid)));
        Assertions.assertEquals(ComplianceRuleAvailabilityStatus.NOT_AVAILABLE, providerRules.getGroups().stream().filter(r -> r.getUuid().equals(complianceV2GroupUuid)).findFirst().orElseThrow().getAvailabilityStatus());
        Assertions.assertEquals(ComplianceRuleAvailabilityStatus.UPDATED, providerRules.getGroups().stream().filter(r -> r.getUuid().equals(complianceV2Group2Uuid)).findFirst().orElseThrow().getAvailabilityStatus());
    }

    @Test
    void testCreateInternalRule() throws Exception {
        ConditionItemRequestDto conditionItemRequestDto = new ConditionItemRequestDto();
        conditionItemRequestDto.setFieldSource(FilterFieldSource.PROPERTY);
        conditionItemRequestDto.setFieldIdentifier(FilterField.SUBJECT_TYPE.name());
        conditionItemRequestDto.setOperator(FilterConditionOperator.EQUALS);
        conditionItemRequestDto.setValue(CertificateSubjectType.ROOT_CA.getCode());

        ComplianceInternalRuleRequestDto req = new ComplianceInternalRuleRequestDto();
        req.setName("TestInternalRule");
        req.setDescription("Description create");
        req.setResource(Resource.CERTIFICATE);
        req.setConditionItems(List.of(conditionItemRequestDto)); // no condition items
        Assertions.assertThrows(AlreadyExistException.class, () -> complianceProfileService.createComplianceInternalRule(req));

        req.setName("TestInternalRule_Create");
        ComplianceRuleListDto created = complianceProfileService.createComplianceInternalRule(req);
        UUID createdUuid = created.getUuid();

        var opt = internalRuleRepository.findByUuid(createdUuid);
        Assertions.assertTrue(opt.isPresent(), "Internal rule must be persisted");
        ComplianceInternalRule entity = opt.get();
        Assertions.assertEquals("TestInternalRule_Create", entity.getName());
        Assertions.assertEquals("Description create", entity.getDescription());
        Assertions.assertEquals(Resource.CERTIFICATE, entity.getResource());
        Assertions.assertEquals(1, entity.getConditionItems().size());
        Assertions.assertEquals(FilterField.SUBJECT_TYPE.name(), entity.getConditionItems().stream().findFirst().orElseThrow().getFieldIdentifier());
    }

    @Test
    void testUpdateInternalRule() throws Exception {
        ConditionItemRequestDto conditionItemRequestDto = new ConditionItemRequestDto();
        conditionItemRequestDto.setFieldSource(FilterFieldSource.PROPERTY);
        conditionItemRequestDto.setFieldIdentifier(FilterField.SUBJECT_TYPE.name());
        conditionItemRequestDto.setOperator(FilterConditionOperator.EQUALS);
        conditionItemRequestDto.setValue(CertificateSubjectType.ROOT_CA.getCode());

        // create initial rule
        ComplianceInternalRuleRequestDto createReq = new ComplianceInternalRuleRequestDto();
        createReq.setName("TestInternalRule_Update");
        createReq.setDescription("Initial desc");
        createReq.setResource(Resource.CERTIFICATE);
        createReq.setConditionItems(List.of(conditionItemRequestDto));

        ComplianceRuleListDto created = complianceProfileService.createComplianceInternalRule(createReq);
        UUID uuid = created.getUuid();

        // prepare update
        conditionItemRequestDto.setValue(CertificateSubjectType.INTERMEDIATE_CA.getCode());
        ConditionItemRequestDto conditionItemRequestDto2 = new ConditionItemRequestDto();
        conditionItemRequestDto2.setFieldSource(FilterFieldSource.PROPERTY);
        conditionItemRequestDto2.setFieldIdentifier(FilterField.KEY_SIZE.name());
        conditionItemRequestDto2.setOperator(FilterConditionOperator.EQUALS);
        conditionItemRequestDto2.setValue(1024);

        ComplianceInternalRuleRequestDto updateReq = new ComplianceInternalRuleRequestDto();
        updateReq.setName("TestInternalRule_UpdatedName");
        updateReq.setDescription("Updated description");
        updateReq.setResource(Resource.CERTIFICATE);
        updateReq.setConditionItems(List.of(conditionItemRequestDto, conditionItemRequestDto2));

        complianceProfileService.updateComplianceInternalRule(uuid, updateReq);

        // verify persisted changes
        var opt = internalRuleRepository.findByUuid(uuid);
        Assertions.assertTrue(opt.isPresent(), "Updated internal rule must exist");
        ComplianceInternalRule entity = opt.get();
        Assertions.assertEquals("TestInternalRule_UpdatedName", entity.getName());
        Assertions.assertEquals("Updated description", entity.getDescription());
        Assertions.assertEquals(2, entity.getConditionItems().size());
        Assertions.assertEquals(FilterField.SUBJECT_TYPE.name(), entity.getConditionItems().stream().findFirst().orElseThrow().getFieldIdentifier());
    }

    @Test
    void testDeleteInternalRule() throws Exception {
        // delete associated internal rule
        Assertions.assertThrows(ValidationException.class, () -> complianceProfileService.deleteComplianceInternalRule(internalRuleUuid));

        // create rule to delete
        ComplianceInternalRuleRequestDto createReq = new ComplianceInternalRuleRequestDto();
        createReq.setName("TestInternalRule_Delete");
        createReq.setDescription("To be deleted");
        createReq.setResource(Resource.CERTIFICATE);
        createReq.setConditionItems(List.of());

        ComplianceRuleListDto created = complianceProfileService.createComplianceInternalRule(createReq);
        UUID uuid = created.getUuid();

        // delete
        complianceProfileService.deleteComplianceInternalRule(uuid);

        // verify removed
        var opt = internalRuleRepository.findByUuid(uuid);
        Assertions.assertTrue(opt.isEmpty(), "Internal rule should be removed after delete");
    }

    @Test
    void addRuleTest() throws NotFoundException, ConnectorException {
        ComplianceProfileRulesPatchRequestDto dto = new ComplianceProfileRulesPatchRequestDto();
        dto.setRemoval(false);
        dto.setRuleUuid(complianceV2RuleUuid);
        dto.setConnectorUuid(connectorV2.getUuid());
        dto.setKind(KIND_V2);

        complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);
        ComplianceProfileDto complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().getFirst().getRules().size());

        // add new rule
        dto.setRuleUuid(complianceV2Rule2Uuid);
        complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);

        complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(2, complianceProfileDto.getProviderRules().getFirst().getRules().size());

        // add new rule V1
        dto.setRuleUuid(complianceV1RuleUuid);
        dto.setConnectorUuid(connectorV1.getUuid());
        dto.setKind(KIND_V1);
        complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);

        complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(2, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().stream().filter(r -> r.getKind().equals(KIND_V1)).findFirst().orElseThrow().getRules().size());
    }

    @Test
    void addRule_RuleNotFound() {
        ComplianceProfileRulesPatchRequestDto dto = new ComplianceProfileRulesPatchRequestDto();
        dto.setRemoval(false);
        dto.setRuleUuid(UUID.randomUUID());
        dto.setConnectorUuid(connectorV2.getUuid());
        dto.setKind(KIND_V2);

        Assertions.assertThrows(ConnectorEntityNotFoundException.class, () -> complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto));
    }

    @Test
    void deleteRuleTest() throws NotFoundException, ConnectorException {
        ComplianceProfileRulesPatchRequestDto dto = new ComplianceProfileRulesPatchRequestDto();
        dto.setRemoval(true);
        dto.setRuleUuid(internalRuleUuid);

        complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);
        ComplianceProfileDto complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(0, complianceProfileDto.getInternalRules().size());
    }

    @Test
    void deleteRule_RuleNotFound() {
        ComplianceProfileRulesPatchRequestDto dto = new ComplianceProfileRulesPatchRequestDto();
        dto.setRemoval(true);
        dto.setRuleUuid(UUID.randomUUID());

        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto));
    }

    @Test
    void addGroupTest() throws NotFoundException, ConnectorException {
        // add group which is already in the profile
        ComplianceProfileGroupsPatchRequestDto dto = new ComplianceProfileGroupsPatchRequestDto();
        dto.setRemoval(false);
        dto.setGroupUuid(complianceV2GroupUuid);
        dto.setConnectorUuid(connectorV2.getUuid());
        dto.setKind(KIND_V2);

        complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);
        ComplianceProfileDto complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().getFirst().getGroups().size());

        // add new group
        dto.setGroupUuid(complianceV2Group2Uuid);
        complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);

        complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(2, complianceProfileDto.getProviderRules().getFirst().getGroups().size());

        // add new rule V1
        dto.setGroupUuid(complianceV1GroupUuid);
        dto.setConnectorUuid(connectorV1.getUuid());
        dto.setKind(KIND_V1);
        complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);

        complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(2, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().stream().filter(r -> r.getKind().equals(KIND_V1)).findFirst().orElseThrow().getGroups().size());
    }

    @Test
    void addGroup_GroupNotFound() {
        ComplianceProfileGroupsPatchRequestDto dto = new ComplianceProfileGroupsPatchRequestDto();
        dto.setRemoval(false);
        dto.setGroupUuid(UUID.randomUUID());
        dto.setConnectorUuid(connectorV2.getUuid());
        dto.setKind(KIND_V2);

        Assertions.assertThrows(ConnectorEntityNotFoundException.class, () -> complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto));
    }

    @Test
    void deleteGroupTest() throws NotFoundException, ConnectorException {
        ComplianceProfileGroupsPatchRequestDto dto = new ComplianceProfileGroupsPatchRequestDto();
        dto.setRemoval(true);
        dto.setGroupUuid(complianceV2GroupUuid);
        dto.setConnectorUuid(connectorV2.getUuid());
        dto.setKind(KIND_V2);

        complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);
        ComplianceProfileDto complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(0, complianceProfileDto.getProviderRules().getFirst().getGroups().size());
    }

    @Test
    void deleteGroup_GroupNotFound() {
        ComplianceProfileGroupsPatchRequestDto dto = new ComplianceProfileGroupsPatchRequestDto();
        dto.setRemoval(true);
        dto.setGroupUuid(UUID.randomUUID());
        dto.setConnectorUuid(connectorV2.getUuid());
        dto.setKind(KIND_V2);

        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto));
    }

    @Test
    void removeComplianceProfileTest() {
        SecuredUUID complianceProfileUuid = complianceProfile.getSecuredUuid();
        Assertions.assertThrows(ValidationException.class, () -> complianceProfileService.deleteComplianceProfile(complianceProfileUuid));
    }

    @Test
    void testBulkDeleteComplianceProfiles() {
        // create profile that can be deleted
        ComplianceProfile profile1 = new ComplianceProfile();
        profile1.setName("BulkDeleteProfile1");
        profile1.setDescription("desc1");
        profile1 = complianceProfileRepository.save(profile1);

        // create profile that has an association -> deletion without force should fail
        ComplianceProfile profile2 = new ComplianceProfile();
        profile2.setName("BulkDeleteProfile2");
        profile2.setDescription("desc2");
        profile2 = complianceProfileRepository.save(profile2);

        ComplianceProfileAssociation assoc = new ComplianceProfileAssociation();
        assoc.setComplianceProfileUuid(profile2.getUuid());
        assoc.setResource(Resource.RA_PROFILE);
        assoc.setObjectUuid(UUID.randomUUID());
        complianceProfileAssociationRepository.save(assoc);

        // perform bulk delete
        List<SecuredUUID> uuids = List.of(SecuredUUID.fromUUID(profile1.getUuid()), SecuredUUID.fromUUID(profile2.getUuid()));
        List<BulkActionMessageDto> messages = complianceProfileService.bulkDeleteComplianceProfiles(uuids);

        // profile1 should be removed
        Assertions.assertFalse(complianceProfileRepository.findById(profile1.getUuid()).isPresent(), "Profile without associations should be deleted");

        // profile2 should remain and produce one failure message
        Assertions.assertTrue(complianceProfileRepository.findById(profile2.getUuid()).isPresent(), "Profile with association should not be deleted");
        Assertions.assertEquals(1, messages.size(), "One failure message expected for the profile that could not be deleted");
        Assertions.assertEquals(profile2.getUuid().toString(), messages.getFirst().getUuid(), "Failure message should reference the failing profile UUID");
        Assertions.assertNotNull(messages.getFirst().getMessage());
        Assertions.assertFalse(messages.getFirst().getMessage().isBlank());
    }

    @Test
    void forceRemoveComplianceProfileTest() {
        complianceProfileService.forceDeleteComplianceProfiles(List.of(SecuredUUID.fromUUID(complianceProfile.getUuid())));
        Assertions.assertDoesNotThrow(() -> complianceProfileRepository.findAll().size());
    }

    @Test
    void removeComplianceProfile_NotFound() {
        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.deleteComplianceProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void getAssociations() throws NotFoundException {
        var associations = complianceProfileService.getAssociations(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, associations.size());
        Assertions.assertEquals(Resource.RA_PROFILE, associations.getFirst().getResource());
        Assertions.assertEquals(associatedRaProfileUuid, associations.getFirst().getObjectUuid());

        var complianceProfiles = complianceProfileService.getAssociatedComplianceProfiles(Resource.RA_PROFILE, associatedRaProfileUuid);
        Assertions.assertEquals(1, complianceProfiles.size());
        Assertions.assertEquals(complianceProfile.getUuid(), complianceProfiles.getFirst().getUuid());
    }

    @Test
    void associateRaProfile() throws NotFoundException, AlreadyExistException {
        Assertions.assertThrows(AlreadyExistException.class, () -> complianceProfileService.associateComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()), Resource.RA_PROFILE, associatedRaProfileUuid));
        complianceProfileService.associateComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()), Resource.RA_PROFILE, unassociatedRaProfileUuid);

        var associations = complianceProfileService.getAssociations(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(2, associations.size());
    }

    @Test
    void testDisassociateProfile() throws NotFoundException {
        Certificate archivedCertificate = new Certificate();
        archivedCertificate.setArchived(true);
        archivedCertificate.setRaProfileUuid(associatedRaProfileUuid);
        archivedCertificate.setComplianceStatus(ComplianceStatus.OK);
        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("c");
        certificateContentRepository.save(certificateContent);
        archivedCertificate.setCertificateContent(certificateContent);
        certificateRepository.save(archivedCertificate);
        Certificate notArchivedCertificate = new Certificate();
        notArchivedCertificate.setRaProfileUuid(associatedRaProfileUuid);
        notArchivedCertificate.setComplianceStatus(ComplianceStatus.OK);
        CertificateContent certificateContent2 = new CertificateContent();
        certificateContent2.setContent("c2");
        certificateContentRepository.save(certificateContent2);
        notArchivedCertificate.setCertificateContent(certificateContent2);
        certificateRepository.save(notArchivedCertificate);

        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.disassociateComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()), Resource.RA_PROFILE, unassociatedRaProfileUuid));
        complianceProfileService.disassociateComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()), Resource.RA_PROFILE, associatedRaProfileUuid);

        var associations = complianceProfileService.getAssociations(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(0, associations.size());

        // later when compliance check is redone, the status will be set to NOT_CHECKED and assertion will pass
    }

    @Test
    void getComplianceRulesTest_Invalid() {
        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.getComplianceRules(UUID.randomUUID(), "random", null, null, null));
    }

    @Test
    void getComplianceRulesTest() throws ConnectorException, NotFoundException {
        var rules = complianceProfileService.getComplianceRules(null, null, null, null, null);
        Assertions.assertEquals(2, rules.size());

        rules = complianceProfileService.getComplianceRules(connectorV1.getUuid(), KIND_V1, null, null, null);
        Assertions.assertEquals(2, rules.size());

        rules = complianceProfileService.getComplianceRules(connectorV2.getUuid(), KIND_V2, null, null, null);
        Assertions.assertEquals(2, rules.size());
    }

    @Test
    void getComplianceGroupsTest_Invalid() {
        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.getComplianceGroups(UUID.randomUUID(), null, null));
    }

    @Test
    void getComplianceGroupsTest() throws ConnectorException, NotFoundException {
        var groups = complianceProfileService.getComplianceGroups(connectorV1.getUuid(), KIND_V1, null);
        Assertions.assertEquals(1, groups.size());

        groups = complianceProfileService.getComplianceGroups(connectorV2.getUuid(), KIND_V2, null);
        Assertions.assertEquals(2, groups.size());
    }

    @Test
    void getComplianceGroupRulesTest() throws ConnectorException, NotFoundException {
        var groups = complianceProfileService.getComplianceGroupRules(complianceV2Group2Uuid, connectorV2.getUuid(), KIND_V2);
        Assertions.assertEquals(1, groups.size());
    }
}

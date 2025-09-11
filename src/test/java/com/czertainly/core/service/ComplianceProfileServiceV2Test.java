package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.compliance.v2.*;
import com.czertainly.api.model.connector.compliance.v2.ComplianceRuleRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileListDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.workflows.RuleRepository;
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

    private static final String KIND = "default";

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
    RuleService ruleService;
    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    private Connector connector;
    private WireMockServer mockServer;
    private ComplianceProfile complianceProfile;

    private UUID associatedRaProfileUuid;
    private UUID unassociatedRaProfileUuid;

    private UUID internalRuleUuid;
    private UUID internalRule2Uuid;
    private final UUID complianceRuleUuid = UUID.randomUUID();
    private final UUID complianceRule2Uuid = UUID.randomUUID();
    private final UUID complianceGroupUuid = UUID.randomUUID();
    private final UUID complianceGroup2Uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() throws NotFoundException, AlreadyExistException {
        mockComplianceProvider();

        connector = new Connector();
        connector.setName("Sample Connector");
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        FunctionGroup functionGroup = new FunctionGroup();
        functionGroup.setCode(FunctionGroupCode.COMPLIANCE_PROVIDER_V2);
        functionGroup.setName(FunctionGroupCode.COMPLIANCE_PROVIDER_V2.getCode());
        functionGroupRepository.save(functionGroup);

        Connector2FunctionGroup c2fg = new Connector2FunctionGroup();
        c2fg.setConnector(connector);
        c2fg.setConnectorUuid(connector.getUuid());
        c2fg.setFunctionGroup(functionGroup);
        c2fg.setFunctionGroupUuid(functionGroup.getUuid());
        c2fg.setKinds(MetaDefinitions.serializeArrayString(List.of(KIND)));
        connector2FunctionGroupRepository.save(c2fg);
        connector.getFunctionGroups().add(c2fg);
        connectorRepository.save(connector);

        complianceProfile = new ComplianceProfile();
        complianceProfile.setName("TestProfile");
        complianceProfile.setDescription("Sample Description");
        complianceProfileRepository.save(complianceProfile);

        createInternalRule();
        createComplianceProfileAssociations();

        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
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

    private void createInternalRule() throws NotFoundException, AlreadyExistException {
        ConditionItemRequestDto conditionItemRequestDto = new ConditionItemRequestDto();
        conditionItemRequestDto.setFieldSource(FilterFieldSource.PROPERTY);
        conditionItemRequestDto.setFieldIdentifier("KEY_SIZE");
        conditionItemRequestDto.setOperator(FilterConditionOperator.EQUALS);
        conditionItemRequestDto.setValue(1024);

        ConditionRequestDto conditionRequestDto = new ConditionRequestDto();
        conditionRequestDto.setName("TestCond");
        conditionRequestDto.setResource(Resource.CERTIFICATE);
        conditionRequestDto.setType(ConditionType.CHECK_FIELD);
        conditionRequestDto.setItems(List.of(conditionItemRequestDto));
        ConditionDto conditionDto = ruleService.createCondition(conditionRequestDto);

        RuleRequestDto ruleRequestDto = new RuleRequestDto();
        ruleRequestDto.setName("TestInternalRule");
        ruleRequestDto.setResource(Resource.CERTIFICATE);
        ruleRequestDto.setConditionsUuids(List.of(conditionDto.getUuid()));
        RuleDetailDto ruleDetailDto = ruleService.createRule(ruleRequestDto);
        internalRuleUuid = UUID.fromString(ruleDetailDto.getUuid());

        conditionItemRequestDto.setFieldIdentifier("PRIVATE_KEY");
        conditionItemRequestDto.setValue(true);
        conditionRequestDto.setName("TestCond2");
        ruleRequestDto.setName("TestInternalRule2");
        ruleDetailDto = ruleService.createRule(ruleRequestDto);
        internalRule2Uuid = UUID.fromString(ruleDetailDto.getUuid());
    }

    private void createComplianceProfileAssociations() {
        var complianceProfileRule = new ComplianceProfileRule();
        complianceProfileRule.setComplianceProfile(complianceProfile);
        complianceProfileRule.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileRule.setResource(Resource.CERTIFICATE);
        complianceProfileRule.setConnectorUuid(connector.getUuid());
        complianceProfileRule.setKind(KIND);
        complianceProfileRule.setComplianceRuleUuid(complianceRuleUuid);
        complianceProfileRuleRepository.save(complianceProfileRule);

        ComplianceProfileRule complianceProfileRule2 = new ComplianceProfileRule();
        complianceProfileRule2.setComplianceProfile(complianceProfile);
        complianceProfileRule2.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileRule2.setResource(Resource.CERTIFICATE);
        complianceProfileRule2.setConnectorUuid(connector.getUuid());
        complianceProfileRule2.setKind(KIND);
        complianceProfileRule2.setComplianceGroupUuid(complianceGroupUuid);
        complianceProfileRuleRepository.save(complianceProfileRule2);

        ComplianceProfileRule complianceProfileRule3 = new ComplianceProfileRule();
        complianceProfileRule3.setComplianceProfile(complianceProfile);
        complianceProfileRule3.setComplianceProfileUuid(complianceProfile.getUuid());
        complianceProfileRule3.setResource(Resource.CERTIFICATE);
        complianceProfileRule3.setInternalRuleUuid(internalRuleUuid);
        complianceProfileRuleRepository.save(complianceProfileRule3);
    }

    private void mockComplianceProvider() {
        mockServer = new WireMockServer(0);
        mockServer.start();

        String complianceRuleResponse = """
                {
                  "uuid": "%s",
                  "name": "Rule1",
                  "description": "Description",
                  "resource": "certificates",
                  "type": "X.509"
                }
                """.formatted(complianceRuleUuid);

        String complianceRule2Response = """
                {
                  "uuid": "%s",
                  "name": "Rule2",
                  "description": "Description2",
                  "groupUuid": "%s",
                  "resource": "certificates",
                  "type": "X.509",
                  "attributes": []
                }
                """.formatted(complianceRule2Uuid, complianceGroup2Uuid);

        String complianceGroupResponse = """
                {
                  "uuid": "%s",
                  "name": "Group1",
                  "description": "Sample description"
                }
                """.formatted(complianceGroupUuid);

        String complianceGroup2Response = """
                {
                  "uuid": "%s",
                  "name": "Group2",
                  "description": "Sample description",
                  "resource": "certificates"
                }
                """.formatted(complianceGroup2Uuid);

        WireMock.configureFor("localhost", mockServer.port());

        WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v2/complianceProvider/%s/rules/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".formatted(KIND)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(new NotFoundException("Group", "<UUID>").getMessage())
                        .withStatus(404)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules".formatted(KIND)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  %s,
                                  %s
                                ]
                                """.formatted(complianceRuleResponse, complianceRule2Response))
                        .withStatus(200)));

        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules".formatted(KIND)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
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
                                """.formatted(complianceRuleResponse, complianceRule2Response, complianceGroupResponse, complianceGroup2Response))
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules/%s".formatted(KIND, complianceRuleUuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceRuleResponse)
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules/%s".formatted(KIND, complianceRule2Uuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceRule2Response)
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v2/complianceProvider/%s/groups/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".formatted(KIND)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(new NotFoundException("Group", "<UUID>").getMessage())
                        .withStatus(404)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/complianceProvider/%s/groups".formatted(KIND)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  %s,
                                  %s
                                ]
                                """.formatted(complianceGroupResponse, complianceGroup2Response))
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s".formatted(KIND, complianceGroupUuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceGroupResponse)
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s".formatted(KIND, complianceGroup2Uuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceGroup2Response)
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s/rules".formatted(KIND, complianceGroupUuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")
                        .withStatus(200)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/groups/%s/rules".formatted(KIND, complianceGroup2Uuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceGroup2Response)
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
    void updateComplianceProfileTest() throws AlreadyExistException, AttributeException, NotFoundException, ConnectorException {
        ComplianceProfileUpdateRequestDto requestDto = new ComplianceProfileUpdateRequestDto();
        requestDto.setDescription("sampleDescription2");
        requestDto.setInternalRules(Set.of(internalRuleUuid, internalRule2Uuid));

        ComplianceRuleRequestDto ruleRequestDto = new ComplianceRuleRequestDto();
        ruleRequestDto.setUuid(complianceRuleUuid);
        ComplianceRuleRequestDto ruleRequestDto2 = new ComplianceRuleRequestDto();
        ruleRequestDto2.setUuid(complianceRule2Uuid);

        ProviderComplianceRulesRequestDto providerRequestDto = new ProviderComplianceRulesRequestDto();
        providerRequestDto.setConnectorUuid(connector.getUuid());
        providerRequestDto.setKind(KIND);
        providerRequestDto.setRules(Set.of(ruleRequestDto, ruleRequestDto2));
        providerRequestDto.setGroups(Set.of(complianceGroupUuid, complianceGroup2Uuid));
        requestDto.getProviderRules().add(providerRequestDto);

        ComplianceProfileDto dto = complianceProfileService.updateComplianceProfile(complianceProfile.getSecuredUuid(), requestDto);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(2, dto.getInternalRules().size());
        Assertions.assertEquals(1, dto.getProviderRules().size());
        Assertions.assertEquals(2, dto.getProviderRules().getFirst().getRules().size());
        Assertions.assertEquals(2, dto.getProviderRules().getFirst().getGroups().size());
        Assertions.assertEquals("sampleDescription2", dto.getDescription());
    }

    @Test
    void addRuleTest() throws NotFoundException, ConnectorException {
        ComplianceProfileRulesPatchRequestDto dto = new ComplianceProfileRulesPatchRequestDto();
        dto.setRemoval(false);
        dto.setRuleUuid(complianceRuleUuid);
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(KIND);

        complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);
        ComplianceProfileDto complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().getFirst().getRules().size());

        // add new rule
        dto.setRuleUuid(complianceRule2Uuid);
        complianceProfileService.patchComplianceProfileRules(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);

        complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(2, complianceProfileDto.getProviderRules().getFirst().getRules().size());
    }

    @Test
    void addRule_RuleNotFound() {
        ComplianceProfileRulesPatchRequestDto dto = new ComplianceProfileRulesPatchRequestDto();
        dto.setRemoval(false);
        dto.setRuleUuid(UUID.randomUUID());
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(KIND);

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
        dto.setGroupUuid(complianceGroupUuid);
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(KIND);

        complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);
        ComplianceProfileDto complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().getFirst().getGroups().size());

        // add new group
        dto.setGroupUuid(complianceGroup2Uuid);
        complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto);

        complianceProfileDto = complianceProfileService.getComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid()));
        Assertions.assertEquals(1, complianceProfileDto.getProviderRules().size());
        Assertions.assertEquals(2, complianceProfileDto.getProviderRules().getFirst().getGroups().size());
    }

    @Test
    void addGroup_GroupNotFound() {
        ComplianceProfileGroupsPatchRequestDto dto = new ComplianceProfileGroupsPatchRequestDto();
        dto.setRemoval(false);
        dto.setGroupUuid(UUID.randomUUID());
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(KIND);

        Assertions.assertThrows(ConnectorEntityNotFoundException.class, () -> complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto));
    }

    @Test
    void deleteGroupTest() throws NotFoundException, ConnectorException {
        ComplianceProfileGroupsPatchRequestDto dto = new ComplianceProfileGroupsPatchRequestDto();
        dto.setRemoval(true);
        dto.setGroupUuid(complianceGroupUuid);
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(KIND);

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
        dto.setConnectorUuid(connector.getUuid());
        dto.setKind(KIND);

        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.patchComplianceProfileGroups(SecuredUUID.fromUUID(complianceProfile.getUuid()), dto));
    }

    @Test
    void removeComplianceProfileTest() {
        Assertions.assertThrows(ValidationException.class, () -> complianceProfileService.deleteComplianceProfile(SecuredUUID.fromUUID(complianceProfile.getUuid())));
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
    void testDisassociateProfile() throws NotFoundException, ConnectorException {
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

        // archivedCertificate = certificateRepository.findWithAssociationsByUuid(archivedCertificate.getUuid()).get();
        // notArchivedCertificate = certificateRepository.findWithAssociationsByUuid(notArchivedCertificate.getUuid()).get();
        // Assertions.assertEquals(ComplianceStatus.OK, archivedCertificate.getComplianceStatus());
        // Assertions.assertEquals(ComplianceStatus.NOT_CHECKED, notArchivedCertificate.getComplianceStatus());

    }

    @Test
    void getComplianceRulesTest_Invalid() {
        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.getComplianceRules(UUID.randomUUID(), "random", null, null, null));
    }

    @Test
    void getComplianceGroupsTest_Invalid() {
        Assertions.assertThrows(NotFoundException.class, () -> complianceProfileService.getComplianceGroups(UUID.randomUUID(), "random", null));
    }

    @Test
    void getComplianceGroupRules() throws ConnectorException, NotFoundException {
        var groupRules = complianceProfileService.getComplianceGroupRules(complianceGroupUuid, connector.getUuid(), KIND);
        Assertions.assertEquals(0, groupRules.size());

        groupRules = complianceProfileService.getComplianceGroupRules(complianceGroup2Uuid, connector.getUuid(), KIND);
        Assertions.assertEquals(1, groupRules.size());
    }
}

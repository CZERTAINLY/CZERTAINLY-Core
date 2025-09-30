package com.czertainly.core.service.compliance;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.compliance.v2.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.v2.ComplianceRuleListDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.workflows.ConditionItemRequestDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.service.v2.ComplianceProfileService;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class BaseComplianceTest extends BaseSpringBootTest {

    protected static final String KIND_V1 = "kindV1";
    protected static final String KIND_V2 = "kindV2";

    @Autowired
    protected ComplianceProfileRepository complianceProfileRepository;

    @Autowired
    protected ComplianceProfileService complianceProfileService;

    @Autowired
    protected ComplianceProfileRuleRepository complianceProfileRuleRepository;

    @Autowired
    protected ComplianceProfileAssociationRepository complianceProfileAssociationRepository;

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private FunctionGroupRepository functionGroupRepository;
    @Autowired
    private Connector2FunctionGroupRepository connector2FunctionGroupRepository;

    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;

    @Autowired
    protected ComplianceInternalRuleRepository internalRuleRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    protected CertificateRepository certificateRepository;
    @Autowired
    protected CertificateContentRepository certificateContentRepository;

    protected Connector connectorV1;
    protected Connector connectorV2;
    private WireMockServer mockServer;
    protected ComplianceProfile complianceProfile;

    protected UUID associatedRaProfileUuid;
    protected UUID unassociatedRaProfileUuid;

    protected UUID internalRuleUuid;
    protected UUID internalRule2Uuid;
    protected final UUID complianceV1RuleUuid = UUID.randomUUID();
    protected final UUID complianceV1Rule2Uuid = UUID.randomUUID();
    protected final UUID complianceV1GroupUuid = UUID.randomUUID();
    protected final UUID complianceV2RuleUuid = UUID.randomUUID();
    protected final UUID complianceV2Rule2Uuid = UUID.randomUUID();
    protected final UUID complianceV2RuleKeyUuid = UUID.randomUUID();
    protected final UUID complianceV2GroupUuid = UUID.randomUUID();
    protected final UUID complianceV2Group2Uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() throws AlreadyExistException {
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

    protected void mockComplianceProviderResponses(boolean defaultResponses) {
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

        String complianceRuleKeyResponse = """
                {
                  "uuid": "%s",
                  "name": "Rule-Key",
                  "description": "DescriptionKey",
                  "resource": "keys",
                  "attributes": [
                    {
                        "version": 2,
                        "uuid": "7ed00886-e706-11ec-8fea-0242ac120002",
                        "name": "KeyLength",
                        "description": "Enter the key size of the certificate to be checked",
                        "type": "data",
                        "contentType": "integer",
                        "properties": {
                            "label": "Key Length",
                            "visible": true,
                            "required": true,
                            "readOnly": false,
                            "list": false,
                            "multiSelect": false
                        }
                    }
                  ]
                }
                """.formatted(complianceV2RuleKeyUuid);


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
                                                  %s,
                                                  %s
                                                ]
                                                """.formatted(complianceRuleResponse, complianceRule2Response, complianceRuleKeyResponse)
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
                                                    %s,
                                                    %s
                                                  ],
                                                  "groups": [
                                                    %s,
                                                    %s
                                                  ]
                                                }
                                                """.formatted(complianceRuleResponse, complianceRule2Response, complianceRuleKeyResponse, complianceGroupResponse, complianceGroup2Response)
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

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/complianceProvider/%s/rules/%s".formatted(KIND_V2, complianceV2RuleKeyUuid)))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(complianceRuleKeyResponse)
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

}

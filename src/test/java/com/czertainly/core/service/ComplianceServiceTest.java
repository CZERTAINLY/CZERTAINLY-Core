package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.compliance.ComplianceGroupRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceRuleAdditionRequestDto;
import com.czertainly.api.model.client.compliance.RaProfileAssociationRequestDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class ComplianceServiceTest extends BaseSpringBootTest {

    private static final String RA_PROFILE_NAME = "testRaProfile1";
    private static final String COMPLIANCE_KIND = "default";

    @Autowired
    private com.czertainly.core.service.RaProfileService raProfileService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private ComplianceService complianceService;
    @Autowired
    private ComplianceProfileService complianceProfileService;
    @Autowired
    private ComplianceProfileRepository complianceProfileRepository;
    @Autowired
    private ComplianceGroupRepository complianceGroupRepository;
    @Autowired
    private ComplianceRuleRepository complianceRuleRepository;


    private RaProfile raProfile;

    private Certificate certificate;
    private Connector connector;
    private ComplianceRule complianceRule;
    private ComplianceGroup complianceGroup;

    private WireMockServer mockServer;
    private WireMockServer mockServer1;

    @BeforeEach
    public void setUp() throws NotFoundException, AlreadyExistException {
        mockServer1 = new WireMockServer(3666);
        mockServer1.start();

        WireMock.configureFor("localhost", mockServer1.port());


        CertificateContent certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("123456789");
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate = certificateRepository.save(certificate);

        connector = new Connector();
        connector.setUrl("http://localhost:3666");
        connector = connectorRepository.save(connector);

        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile = raProfileRepository.save(raProfile);

        ComplianceProfile complianceProfile = new ComplianceProfile();
        complianceProfile.setName("TestProfile");
        complianceProfile.setDescription("Sample Description");
        complianceProfileRepository.save(complianceProfile);

        RaProfileAssociationRequestDto associationRequestDto = new RaProfileAssociationRequestDto();
        associationRequestDto.setRaProfileUuids(List.of(raProfile.getUuid().toString()));
        complianceProfileService.associateProfile(complianceProfile.getSecuredUuid(), associationRequestDto);

        certificate.setRaProfile(raProfile);
        certificateRepository.save(certificate);

        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setName("Sample Connector");
        connector.setUrl("http://localhost:"+mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        complianceGroup = new ComplianceGroup();
        complianceGroup.setName("testGroup");
        complianceGroup.setKind(COMPLIANCE_KIND);
        complianceGroup.setDescription("Sample description");
        complianceGroup.setUuid(UUID.fromString("e8965d90-f1fd-11ec-b939-0242ac120003"));
        complianceGroup.setConnectorUuid(connector.getUuid());
        complianceGroupRepository.save(complianceGroup);

        complianceRule = new ComplianceRule();
        complianceRule.setConnectorUuid(connector.getUuid());
        complianceRule.setKind(COMPLIANCE_KIND);
        complianceRule.setName("Rule1");
        complianceRule.setDescription("Description");
        complianceRule.setUuid(UUID.fromString("e8965d90-f1fd-11ec-b939-0242ac120002"));
        complianceRule.setCertificateType(CertificateType.X509);
        complianceRule.setGroup(complianceGroup);
        complianceRuleRepository.save(complianceRule);

        ComplianceRule complianceRule2 = new ComplianceRule();
        complianceRule2.setConnectorUuid(connector.getUuid());
        complianceRule2.setKind(COMPLIANCE_KIND);
        complianceRule2.setName("Rule2");
        complianceRule2.setDescription("Description2");
        complianceRule2.setUuid(UUID.fromString("e8965d90-f1fd-11ec-b939-0242ac120004"));
        complianceRule2.setCertificateType(CertificateType.X509);
        complianceRuleRepository.save(complianceRule2);

        ComplianceGroupRequestDto complianceGroupRequestDto = new ComplianceGroupRequestDto();
        complianceGroupRequestDto.setConnectorUuid(connector.getUuid().toString());
        complianceGroupRequestDto.setKind(complianceGroup.getKind());
        complianceGroupRequestDto.setGroupUuid(complianceGroup.getUuid().toString());
        complianceProfileService.addGroup(complianceProfile.getSecuredUuid(), complianceGroupRequestDto);

        ComplianceRuleAdditionRequestDto additionRequestDto = new ComplianceRuleAdditionRequestDto();
        additionRequestDto.setConnectorUuid(connector.getUuid().toString());
        additionRequestDto.setKind(complianceRule2.getKind());
        additionRequestDto.setRuleUuid(complianceRule2.getUuid().toString());
        complianceProfileService.addRule(complianceProfile.getSecuredUuid(), additionRequestDto);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
        mockServer1.stop();
    }

    @Test
    void testComplianceCheck() {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/complianceProvider/[^/]+/compliance"))
                .willReturn(WireMock.okJson("{\"status\":\"ok\",\"rules\":[\"uuid\":\"e8965d90-f1fd-11ec-b939-0242ac120002\", \"name\":\"tests\", \"status\":\"ok\"]}")));
        Assertions.assertDoesNotThrow(() -> complianceService.checkComplianceOfCertificate(certificate));
    }

    @Test
    void testComplianceCheck_RaProfile() {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/complianceProvider/[^/]+/compliance"))
                .willReturn(WireMock.okJson("{\"status\":\"ok\",\"rules\":[]}")));
        Assertions.assertDoesNotThrow(() -> complianceService.complianceCheckForRaProfile(SecuredUUID.fromString(raProfile.getUuid().toString())));
    }

    @Test
    void checkRuleExistsTest(){
        Boolean isExists = complianceService.complianceRuleExists(SecuredUUID.fromString(complianceRule.getUuid().toString()), connector, "default");
        Assertions.assertEquals(true, isExists);
    }

    @Test
    void checkRuleNotExistsTest(){
        Boolean isExists = complianceService.complianceRuleExists(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), connector, "default");
        Assertions.assertEquals(false, isExists);
    }

    @Test
    void checkGroupExistsTest(){
        Boolean isExists = complianceService.complianceGroupExists(SecuredUUID.fromString(complianceGroup.getUuid().toString()), connector, "default");
        Assertions.assertEquals(true, isExists);
    }

    @Test
    void checkGroupNotExistsTest(){
        Boolean isExists = complianceService.complianceGroupExists(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), connector, "default");
        Assertions.assertEquals(false, isExists);
    }
}

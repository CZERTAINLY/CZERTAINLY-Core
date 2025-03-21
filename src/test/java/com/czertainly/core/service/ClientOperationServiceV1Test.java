package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.authority.ClientAddEndEntityRequestDto;
import com.czertainly.api.model.client.authority.LegacyClientCertificateRevocationDto;
import com.czertainly.api.model.client.authority.LegacyClientCertificateSignRequestDto;
import com.czertainly.api.model.client.authority.ClientEditEndEntityRequestDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.BaseSpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

public class ClientOperationServiceV1Test extends BaseSpringBootTest {

    public static final String RA_PROFILE_NAME = "testRaProfile1";

    @Autowired
    private ClientOperationService clientOperationService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    private RaProfile raProfile;
    private AuthorityInstanceReference authorityInstanceReference;
    private Connector connector;
    private Certificate certificate;
    private CertificateContent certificateContent;

    private WireMockServer mockServer;

    private X509Certificate x509Cert;
    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }


    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException, NotFoundException, AttributeException {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setUrl("http://localhost:"+mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference.setConnectorUuid(connector.getUuid());
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        // prepare attribute
        DataAttribute attribute = new DataAttribute();
        attribute.setUuid("6e9146a6-da8a-403f-99cb-d5d64d93ce1d");
        attribute.setName("endEntityProfile");
        DataAttributeProperties properties = new DataAttributeProperties();
        properties.setLabel("End entity");
        attribute.setDescription("description");
        attribute.setContentType(AttributeContentType.OBJECT);
        attribute.setType(AttributeType.DATA);
        properties.setRequired(true);
        properties.setReadOnly(false);
        properties.setVisible(true);
        attribute.setProperties(properties);
        attributeEngine.updateDataAttributeDefinitions(connector.getUuid(), null, List.of(attribute));

        raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile.setAuthorityInstanceReferenceUuid(authorityInstanceReference.getUuid());
        raProfile.setEnabled(true);

        ObjectAttributeContent contentMap = new ObjectAttributeContent();
        contentMap.setReference("1");
        contentMap.setData(new NameAndIdDto(1, "profile"));

        raProfile = raProfileRepository.save(raProfile);
        attributeEngine.updateObjectDataAttributesContent(connector.getUuid(), null, Resource.RA_PROFILE, raProfile.getUuid(), AttributeDefinitionUtils.createAttributes(attribute.getUuid(), "endEntityProfile", List.of(contentMap)));

        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate = certificateRepository.save(certificate);

        raProfile = raProfileRepository.save(raProfile);

        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyStoreStream, "123456".toCharArray());

        x509Cert = (X509Certificate) keyStore.getCertificate("1");
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }

    @Test
    void testIssueCertificate() throws ConnectorException, CertificateException, AlreadyExistException, NoSuchAlgorithmException, NotFoundException {
        String certificateData = Base64.getEncoder().encodeToString(x509Cert.getEncoded());
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/certificates/issue"))
                .willReturn(WireMock.okJson("{ \"certificateData\": \"" + certificateData + "\" }")));

        LegacyClientCertificateSignRequestDto request = new LegacyClientCertificateSignRequestDto();
        Assertions.assertDoesNotThrow(() -> clientOperationService.issueCertificate(RA_PROFILE_NAME, request));
    }

    @Test
    void testIssueCertificate_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.issueCertificate("wrong-name", null));
    }

    @Test
    void testRevokeCertificate() {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/certificates/revoke"))
                .willReturn(WireMock.ok()));

        LegacyClientCertificateRevocationDto request = new LegacyClientCertificateRevocationDto();
        Assertions.assertDoesNotThrow(() -> clientOperationService.revokeCertificate(RA_PROFILE_NAME, request));
    }

    @Test
    void testRevokeCertificate_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.revokeCertificate("wrong-name", null));
    }

    @Test
    void testListEntities() {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities"))
                .willReturn(WireMock.ok()));

        Assertions.assertDoesNotThrow(() -> clientOperationService.listEntities(RA_PROFILE_NAME));
    }

    @Test
    void testListEntities_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.listEntities("wrong-name"));
    }

    @Test
    void testAddEntity() {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities"))
                .willReturn(WireMock.ok()));

        Assertions.assertDoesNotThrow(() -> clientOperationService.addEndEntity(RA_PROFILE_NAME, new ClientAddEndEntityRequestDto()));
    }

    @Test
    void testAddEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.addEndEntity("wrong-name", null));
    }

    @Test
    void testGetEntity() {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        Assertions.assertDoesNotThrow(() -> clientOperationService.getEndEntity(RA_PROFILE_NAME, "testEndEntity"));
    }

    @Test
    void testGetEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.getEndEntity("wrong-name", null));
    }

    @Test
    void testEditEntity() {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        ClientEditEndEntityRequestDto request = new ClientEditEndEntityRequestDto();
        Assertions.assertDoesNotThrow(() -> clientOperationService.editEndEntity(RA_PROFILE_NAME, "testEndEntity", request));
    }

    @Test
    void testEditEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.editEndEntity("wrong-name", null, null));
    }

    @Test
    void testRevokeAndDeleteEndEntity() {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        Assertions.assertDoesNotThrow(() -> clientOperationService.revokeAndDeleteEndEntity(RA_PROFILE_NAME, "testEndEntity"));
    }

    @Test
    void testRevokeAndDeleteEndEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.revokeAndDeleteEndEntity("wrong-name", null));
    }

    @Test
    void testResetPassword() {
        mockServer.stubFor(WireMock
                .put(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+/resetPassword"))
                .willReturn(WireMock.ok()));

        Assertions.assertDoesNotThrow(() -> clientOperationService.resetPassword(RA_PROFILE_NAME, "testEndEntity"));
    }

    @Test
    void testResetPassword_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.resetPassword("wrong-name", null));
    }
}

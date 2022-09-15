package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.authority.ClientAddEndEntityRequestDto;
import com.czertainly.api.model.client.authority.ClientCertificateRevocationDto;
import com.czertainly.api.model.client.authority.ClientCertificateSignRequestDto;
import com.czertainly.api.model.client.authority.ClientEditEndEntityRequestDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

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
    @Autowired
    private ClientRepository clientRepository;

    private RaProfile raProfile;
    private AuthorityInstanceReference authorityInstanceReference;
    private Connector connector;
    private Certificate certificate;
    private CertificateContent certificateContent;
    private Client client;

    private WireMockServer mockServer;

    private X509Certificate x509Cert;

    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference.setConnectorUuid(connector.getUuid());
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile.setAuthorityInstanceReferenceUuid(authorityInstanceReference.getUuid());
        raProfile.setEnabled(true);

        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("value", 1);
        contentMap.put("data", new NameAndIdDto(1, "profile"));


        raProfile.setAttributes(AttributeDefinitionUtils.serialize(
                AttributeDefinitionUtils.clientAttributeConverter(AttributeDefinitionUtils.createAttributes("endEntityProfile", contentMap))
        ));

        raProfile = raProfileRepository.save(raProfile);

        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate = certificateRepository.save(certificate);

        client = new Client();
        client.setName("user");
        client.setCertificateUuid(certificate.getUuid());
        client.setSerialNumber(certificate.getSerialNumber());
        client.getRaProfiles().add(raProfile);
        client = clientRepository.save(client);

        raProfile.getClients().add(client);
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
    public void testIssueCertificate() throws ConnectorException, CertificateException, AlreadyExistException {
        String certificateData = Base64.getEncoder().encodeToString(x509Cert.getEncoded());
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/certificates/issue"))
                .willReturn(WireMock.okJson("{ \"certificateData\": \"" + certificateData + "\" }")));

        ClientCertificateSignRequestDto request = new ClientCertificateSignRequestDto();
        clientOperationService.issueCertificate(RA_PROFILE_NAME, request);
    }

    @Test
    public void testIssueCertificate_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.issueCertificate("wrong-name", null));
    }

    @Test
    public void testRevokeCertificate() throws ConnectorException, CertificateException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/certificates/revoke"))
                .willReturn(WireMock.ok()));

        ClientCertificateRevocationDto request = new ClientCertificateRevocationDto();
        clientOperationService.revokeCertificate(RA_PROFILE_NAME, request);
    }

    @Test
    public void testRevokeCertificate_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.revokeCertificate("wrong-name", null));
    }

    @Test
    public void testListEntities() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities"))
                .willReturn(WireMock.ok()));

        clientOperationService.listEntities(RA_PROFILE_NAME);
    }

    @Test
    public void testListEntities_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.listEntities("wrong-name"));
    }

    @Test
    public void testAddEntity() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities"))
                .willReturn(WireMock.ok()));

        clientOperationService.addEndEntity(RA_PROFILE_NAME, new ClientAddEndEntityRequestDto());
    }

    @Test
    public void testAddEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.addEndEntity("wrong-name", null));
    }

    @Test
    public void testGetEntity() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        clientOperationService.getEndEntity(RA_PROFILE_NAME, "testEndEntity");
    }

    @Test
    public void testGetEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.getEndEntity("wrong-name", null));
    }

    @Test
    public void testEditEntity() throws ConnectorException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        ClientEditEndEntityRequestDto request = new ClientEditEndEntityRequestDto();
        clientOperationService.editEndEntity(RA_PROFILE_NAME, "testEndEntity", request);
    }

    @Test
    public void testEditEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.editEndEntity("wrong-name", null, null));
    }

    @Test
    public void testRevokeAndDeleteEndEntity() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        clientOperationService.revokeAndDeleteEndEntity(RA_PROFILE_NAME, "testEndEntity");
    }

    @Test
    public void testRevokeAndDeleteEndEntity_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.revokeAndDeleteEndEntity("wrong-name", null));
    }

    @Test
    public void testResetPassword() throws ConnectorException {
        mockServer.stubFor(WireMock
                .put(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+/resetPassword"))
                .willReturn(WireMock.ok()));

        clientOperationService.resetPassword(RA_PROFILE_NAME, "testEndEntity");
    }

    @Test
    public void testResetPassword_nonexistentEntity() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.resetPassword("wrong-name", null));
    }
}

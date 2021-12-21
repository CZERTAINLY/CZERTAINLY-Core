package com.czertainly.core.service;

import com.czertainly.api.core.modal.ClientAddEndEntityRequestDto;
import com.czertainly.api.core.modal.ClientCertificateRevocationDto;
import com.czertainly.api.core.modal.ClientCertificateSignRequestDto;
import com.czertainly.api.core.modal.ClientEditEndEntityRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.NameAndIdDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles={ "CLIENT", ClientOperationServiceV1Test.RA_PROFILE_NAME })
public class ClientOperationServiceV1Test {

    public static final String RA_PROFILE_NAME = "testRaProfile1";

    @Autowired
    private ClientOperationService clientOperationService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CAInstanceReferenceRepository caInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private ClientRepository clientRepository;

    private RaProfile raProfile;
    private CAInstanceReference caInstance;
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
        connector = connectorRepository.save(connector);

        caInstance = new CAInstanceReference();
        caInstance.setCaInstanceUuid("1l");
        caInstance.setConnector(connector);
        caInstance = caInstanceReferenceRepository.save(caInstance);

        raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setCaInstanceReference(caInstance);
        raProfile.setEnabled(true);

        raProfile.setAttributes(AttributeDefinitionUtils.serialize(
                AttributeDefinitionUtils.createAttributes("endEntityProfile", new NameAndIdDto(1, "profile"))
        ));

        raProfile = raProfileRepository.save(raProfile);

        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setCertificateContent(certificateContent);
        certificate = certificateRepository.save(certificate);

        client = new Client();
        client.setName("user");
        client.setCertificate(certificate);
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
    public void testIssueCertificate_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.issueCertificate("wrong-name", null));
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
    public void testRevokeCertificate_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.revokeCertificate("wrong-name", null));
    }

    @Test
    public void testListEntities() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities"))
                .willReturn(WireMock.ok()));

        clientOperationService.listEntities(RA_PROFILE_NAME);
    }

    @Test
    public void testListEntities_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.listEntities("wrong-name"));
    }

    @Test
    public void testAddEntity() throws ConnectorException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities"))
                .willReturn(WireMock.ok()));

        clientOperationService.addEndEntity(RA_PROFILE_NAME, new ClientAddEndEntityRequestDto());
    }

    @Test
    public void testAddEntity_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.addEndEntity("wrong-name", null));
    }

    @Test
    public void testGetEntity() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        clientOperationService.getEndEntity(RA_PROFILE_NAME, "testEndEntity");
    }

    @Test
    public void testGetEntity_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.getEndEntity("wrong-name", null));
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
    public void testEditEntity_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.editEndEntity("wrong-name", null, null));
    }

    @Test
    public void testRevokeAndDeleteEndEntity() throws ConnectorException {
        mockServer.stubFor(WireMock
                .delete(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+"))
                .willReturn(WireMock.ok()));

        clientOperationService.revokeAndDeleteEndEntity(RA_PROFILE_NAME, "testEndEntity");
    }

    @Test
    public void testRevokeAndDeleteEndEntity_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.revokeAndDeleteEndEntity("wrong-name", null));
    }

    @Test
    public void testResetPassword() throws ConnectorException {
        mockServer.stubFor(WireMock
                .put(WireMock.urlPathMatching("/v1/authorityProvider/authorities/[^/]+/endEntityProfiles/[^/]+/endEntities/[^/]+/resetPassword"))
                .willReturn(WireMock.ok()));

        clientOperationService.resetPassword(RA_PROFILE_NAME, "testEndEntity");
    }

    @Test
    public void testResetPassword_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.resetPassword("wrong-name", null));
    }
}

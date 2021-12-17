package com.czertainly.core.service;

import com.czertainly.api.core.v2.model.ClientCertificateDataResponseDto;
import com.czertainly.api.core.v2.model.ClientCertificateRenewRequestDto;
import com.czertainly.api.core.v2.model.ClientCertificateRevocationDto;
import com.czertainly.api.core.v2.model.ClientCertificateSignRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.NameAndIdDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.service.v2.ClientOperationService;
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
import java.util.List;
import java.util.Optional;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles={ "CLIENT", ClientOperationServiceV2Test.RA_PROFILE_NAME })
public class ClientOperationServiceV2Test {

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
    public void testListIssueCertificateAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/issue/attributes"))
                .willReturn(WireMock.okJson("[]")));

        List<AttributeDefinition> attributes = clientOperationService.listIssueCertificateAttributes(RA_PROFILE_NAME);
        Assertions.assertNotNull(attributes);
    }

    @Test
    public void testListIssueCertificateAttributes_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.listIssueCertificateAttributes("wrong-name"));
    }

    @Test
    public void testValidateIssueCertificateAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/issue/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        boolean result  = clientOperationService.validateIssueCertificateAttributes(RA_PROFILE_NAME, List.of());
        Assertions.assertTrue(result);
    }

    @Test
    public void testValidateIssueCertificateAttributes_validationFail() {
        Assertions.assertThrows(ValidationException.class,
                () -> clientOperationService.validateIssueCertificateAttributes("wrong-name", null));
    }


    @Test
    public void testIssueCertificate() throws ConnectorException, CertificateException, AlreadyExistException {
        String certificateData = Base64.getEncoder().encodeToString(x509Cert.getEncoded());
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/issue"))
                .willReturn(WireMock.okJson("{ \"certificateData\": \"" + certificateData + "\" }")));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/issue/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/issue/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        ClientCertificateSignRequestDto request = new ClientCertificateSignRequestDto();
        ClientCertificateDataResponseDto response = clientOperationService.issueCertificate(RA_PROFILE_NAME, request);
        Assertions.assertNotNull(response);

        Optional<Certificate> newCertificate = certificateRepository.findBySerialNumberIgnoreCase("177E75F42E95ECB98F831EB57DE27B0BC8C47643");
        Assertions.assertTrue(newCertificate.isPresent());
        Assertions.assertEquals(newCertificate.get().getUuid(), response.getUuid());
    }

    @Test
    public void testIssueCertificate_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.issueCertificate("wrong-name", null));
    }

    @Test
    public void testRenewCertificate() throws ConnectorException, CertificateException, AlreadyExistException {
        String certificateData = Base64.getEncoder().encodeToString(x509Cert.getEncoded());
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/[^/]+/renew"))
                .willReturn(WireMock.okJson("{ \"certificateData\": \"" + certificateData + "\" }")));

        ClientCertificateRenewRequestDto request = new ClientCertificateRenewRequestDto();
        clientOperationService.renewCertificate(RA_PROFILE_NAME, certificate.getSerialNumber(), request);
    }

    @Test
    public void testRenewCertificate_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.renewCertificate("wrong-name", null, null));
    }

    @Test
    public void testListRevokeCertificateAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/revoke/attributes"))
                .willReturn(WireMock.okJson("[]")));

        List<AttributeDefinition> attributes = clientOperationService.listRevokeCertificateAttributes(RA_PROFILE_NAME);
        Assertions.assertNotNull(attributes);
    }

    @Test
    public void testListRevokeCertificateAttributes_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.listRevokeCertificateAttributes("wrong-name"));
    }

    @Test
    public void testValidateRevokeCertificateAttributes() throws ConnectorException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/revoke/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        boolean result  = clientOperationService.validateRevokeCertificateAttributes(RA_PROFILE_NAME, List.of());
        Assertions.assertTrue(result);
    }

    @Test
    public void testValidateRevokeCertificateAttributes_validationFail() {
        Assertions.assertThrows(ValidationException.class,
                () -> clientOperationService.validateRevokeCertificateAttributes("wrong-name", null));
    }

    @Test
    public void testRevokeCertificate() throws ConnectorException, CertificateException, AlreadyExistException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/[^/]+/revoke"))
                .willReturn(WireMock.ok()));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/revoke/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/caConnector/authorities/[^/]+/certificates/revoke/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        ClientCertificateRevocationDto request = new ClientCertificateRevocationDto();
        clientOperationService.revokeCertificate(RA_PROFILE_NAME, certificate.getSerialNumber(), request);
    }

    @Test
    public void testRevokeCertificate_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.revokeCertificate("wrong-name", "wrong-cert-id", null));
    }
}

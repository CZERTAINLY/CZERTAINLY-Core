package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.DataAttributeProperties;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.api.model.core.v2.ClientCertificateRekeyRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.cmp.CmpEntityUtil;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateTestUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.transaction.Transactional;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.A;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.C;

import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
class ClientOperationServiceV2Test extends BaseSpringBootTest {

    public static final String RA_PROFILE_NAME = "testRaProfile1";

    private static final String SAMPLE_PKCS10 = """
            -----BEGIN CERTIFICATE REQUEST-----
            MIICzDCCAbQCAQAwgYYxCzAJBgNVBAYTAkVOMQ0wCwYDVQQIDARub25lMQ0wCwYD
            VQQHDARub25lMRIwEAYDVQQKDAlXaWtpcGVkaWExDTALBgNVBAsMBG5vbmUxGDAW
            BgNVBAMMDyoud2lraXBlZGlhLm9yZzEcMBoGCSqGSIb3DQEJARYNbm9uZUBub25l
            LmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMP/U8RlcCD6E8AL
            PT8LLUR9ygyygPCaSmIEC8zXGJung3ykElXFRz/Jc/bu0hxCxi2YDz5IjxBBOpB/
            kieG83HsSmZZtR+drZIQ6vOsr/ucvpnB9z4XzKuabNGZ5ZiTSQ9L7Mx8FzvUTq5y
            /ArIuM+FBeuno/IV8zvwAe/VRa8i0QjFXT9vBBp35aeatdnJ2ds50yKCsHHcjvtr
            9/8zPVqqmhl2XFS3Qdqlsprzbgksom67OobJGjaV+fNHNQ0o/rzP//Pl3i7vvaEG
            7Ff8tQhEwR9nJUR1T6Z7ln7S6cOr23YozgWVkEJ/dSr6LAopb+cZ88FzW5NszU6i
            57HhA7ECAwEAAaAAMA0GCSqGSIb3DQEBBAUAA4IBAQBn8OCVOIx+n0AS6WbEmYDR
            SspR9xOCoOwYfamB+2Bpmt82R01zJ/kaqzUtZUjaGvQvAaz5lUwoMdaO0X7I5Xfl
            sllMFDaYoGD4Rru4s8gz2qG/QHWA8uPXzJVAj6X0olbIdLTEqTKsnBj4Zr1AJCNy
            /YcG4ouLJr140o26MhwBpoCRpPjAgdYMH60BYfnc4/DILxMVqR9xqK1s98d6Ob/+
            3wHFK+S7BRWrJQXcM8veAexXuk9lHQ+FgGfD0eSYGz0kyP26Qa2pLTwumjt+nBPl
            rfJxaLHwTQ/1988G0H35ED0f9Md5fzoKi5evU1wG5WRxdEUPyt3QUXxdQ69i0C+7
            -----END CERTIFICATE REQUEST-----
            """;

    @Autowired
    private ClientOperationService clientOperationService;

    @MockitoBean
    private CryptographicOperationService cryptographicOperationService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateEventHistoryRepository certificateEventHistoryRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;
    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;
    @Autowired
    private TokenProfileRepository tokenProfileRepository;

    private RaProfile raProfile;
    private AuthorityInstanceReference authorityInstanceReference;
    private Connector connector;
    private Certificate certificate;
    private CertificateContent certificateContent;

    private WireMockServer mockServer;

    private X509Certificate x509Cert;
    private AttributeEngine attributeEngine;

    @Autowired
    void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }


    @BeforeEach
    void setUp() throws GeneralSecurityException, IOException, NotFoundException, AttributeException {
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
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

        raProfile = raProfileRepository.save(raProfile);
        attributeEngine.updateObjectDataAttributesContent(connector.getUuid(), null, Resource.RA_PROFILE, raProfile.getUuid(), AttributeDefinitionUtils.createAttributes(attribute.getUuid(), "endEntityProfile", List.of(new ObjectAttributeContent(new NameAndIdDto(1, "profile")))));

        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setCertificateContent(certificateContent);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate.setRaProfile(raProfile);
        certificate = certificateRepository.save(certificate);

        raProfile = raProfileRepository.save(raProfile);

        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyStoreStream, "123456".toCharArray());

        x509Cert = (X509Certificate) keyStore.getCertificate("1");
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void testListIssueCertificateAttributes() throws ConnectorException, NotFoundException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes"))
                .willReturn(WireMock.okJson("[]")));

        List<BaseAttribute> attributes = clientOperationService.listIssueCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromUUID(raProfile.getUuid()));
        Assertions.assertNotNull(attributes);
    }

    @Test
    void testListIssueCertificateAttributes_validationFail() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.listIssueCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testValidateIssueCertificateAttributes() throws ConnectorException, NotFoundException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        boolean result = clientOperationService.validateIssueCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromUUID(raProfile.getUuid()), List.of());
        Assertions.assertTrue(result);
    }

    @Test
    void testValidateIssueCertificateAttributes_validationFail() {
        Assertions.assertThrows(NotFoundException.class,
                () -> clientOperationService.validateIssueCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), null));
    }


    @Test
    void testIssueCertificate() throws CertificateException {
        String certificateData = Base64.getEncoder().encodeToString(x509Cert.getEncoded());
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue"))
                .willReturn(WireMock.okJson("{ \"certificateData\": \"" + certificateData + "\" }")));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        ClientCertificateSignRequestDto request = new ClientCertificateSignRequestDto();
        request.setRequest(SAMPLE_PKCS10);
        request.setAttributes(List.of());
        Assertions.assertDoesNotThrow(() -> clientOperationService.issueCertificate(authorityInstanceReference.getSecuredParentUuid(), raProfile.getSecuredUuid(), request, null));
    }

    @Test
    void testIssueCertificate_validationFail_disabledRaProfile() {
        raProfile.setEnabled(false);
        raProfile = raProfileRepository.save(raProfile);
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.issueCertificate(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), raProfile.getSecuredUuid(), null, null));
    }

    @Test
    void testRenewCertificate() throws CertificateException {
        String certificateData = Base64.getEncoder().encodeToString(x509Cert.getEncoded());
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/renew"))
                .willReturn(WireMock.okJson("{ \"certificateData\": \"" + certificateData + "\" }")));

        ClientCertificateRenewRequestDto request = ClientCertificateRenewRequestDto.builder().build();
        request.setRequest(SAMPLE_PKCS10);
        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.renewCertificateAction(certificate.getUuid(), request, true));
    }

    @Test
    void testRenewCertificate_validationFail_differentRaProfile() {
        RaProfile raProfile2 = new RaProfile();
        raProfile2.setName("testraprofile2");
        raProfile2.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile2.setAuthorityInstanceReferenceUuid(authorityInstanceReference.getUuid());
        raProfile2.setEnabled(true);
        raProfileRepository.save(raProfile2);

        Assertions.assertThrows(ValidationException.class, () -> clientOperationService.renewCertificate(SecuredParentUUID.fromUUID(raProfile2.getAuthorityInstanceReferenceUuid()), raProfile2.getSecuredUuid(), certificate.getUuid().toString(), null));

        raProfileRepository.delete(raProfile2);
    }

    @Test
    void testListRevokeCertificateAttributes() throws ConnectorException, NotFoundException {
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/revoke/attributes"))
                .willReturn(WireMock.okJson("[]")));

        List<BaseAttribute> attributes = clientOperationService.listRevokeCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromUUID(raProfile.getUuid()));
        Assertions.assertNotNull(attributes);
    }

    @Test
    void testListRevokeCertificateAttributes_validationFail() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.listRevokeCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testValidateRevokeCertificateAttributes() throws ConnectorException, NotFoundException {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/revoke/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        boolean result = clientOperationService.validateRevokeCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromUUID(raProfile.getUuid()), List.of());
        Assertions.assertTrue(result);
    }

    @Test
    void testValidateRevokeCertificateAttributes_validationFail() {
        Assertions.assertThrows(NotFoundException.class,
                () -> clientOperationService.validateRevokeCertificateAttributes(SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), null));
    }

    @Test
    void testRevokeCertificate() {
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/revoke"))
                .willReturn(WireMock.ok()));
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/revoke/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/revoke/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        ClientCertificateRevocationDto request = new ClientCertificateRevocationDto();
        request.setAttributes(List.of());
        Assertions.assertDoesNotThrow(() -> clientOperationService.revokeCertificateAction(certificate.getUuid(), request, true));
    }

    @Test
    void testRekeyCertificate() throws NotFoundException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, AttributeException, InvalidAlgorithmParameterException, CertificateException, SignatureException, InvalidKeyException, OperatorCreationException {
        CertificateContent content = new CertificateContent();
        content.setContent(Base64.getEncoder().encodeToString(CertificateTestUtil.createHybridCertificate().getEncoded()));
        certificateContentRepository.save(content);
        certificate.setCertificateContent(content);
        certificateRepository.save(certificate);
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes/validate"))
                .willReturn(WireMock.okJson("true")));
        ClientCertificateRekeyRequestDto request = new ClientCertificateRekeyRequestDto();
        request.setFormat(CertificateRequestFormat.PKCS10);
        CryptographicKey key = createCryptographicKey();
        CryptographicKey altKey = createCryptographicKey();
        request.setKeyUuid(key.getUuid());
        request.setTokenProfileUuid(key.getTokenProfileUuid());
        request.setSignatureAttributes(List.of());
        request.setAltKeyUuid(altKey.getUuid());
        request.setAltTokenProfileUuid(altKey.getTokenProfileUuid());
        request.setAltSignatureAttributes(List.of());
        Mockito.when(cryptographicOperationService.generateCsr(eq(key.getUuid()), eq(key.getTokenProfileUuid()), any(), anyList(), eq(altKey.getUuid()), eq(altKey.getTokenProfileUuid()), anyList())).thenReturn("MIIBUjCBvAIBADATMREwDwYDVQQDDAhuZXdfY2VydDCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEA52WsWllsOi/XtK8VcKHN63Mhk6awMboP9iuwgtPXzkFLV/wILHH+YPAJcS8dP037SZQlAng9dF+IoLHn7WFYmQqqgkObWoH1+5LxHjkPRRNPJLKPtxfM/V+IafsddK7a5TiVD+PiKjoWQaGHVEieozV1fK2BfqVbenKbYMupGVkCAwEAAaAAMA0GCSqGSIb3DQEBBAUAA4GBALtgmv31dFCSO+KnXWeaGEVr2H8g6O0D/RS8xoTRF4yHIgU84EXL5ZWUxhLF6mAXP1de0IfeEf95gGrU9FQ7tdUnwfsBZCIhHOQ/PdzVhRRhaVaPK8N+/g1GyXM/mC074u8y+VoyhHTqAlnbGwzyJkLnVwJ0/jLiRaTdvn7zFDWr");
        Assertions.assertDoesNotThrow(() -> clientOperationService.rekeyCertificate(authorityInstanceReference.getSecuredParentUuid(), raProfile.getSecuredUuid(), String.valueOf(certificate.getUuid()), request));
    }

    private CryptographicKey createCryptographicKey() {
        CryptographicKey cryptographicKey = new CryptographicKey();
        TokenProfile tokenProfile = new TokenProfile();
        tokenProfileRepository.save(tokenProfile);
        cryptographicKey.setTokenProfileUuid(tokenProfile.getUuid());
        cryptographicKeyRepository.save(cryptographicKey);

        CryptographicKeyItem privateKey = new CryptographicKeyItem();
        privateKey.setKey(cryptographicKey);
        privateKey.setType(KeyType.PRIVATE_KEY);
        privateKey.setEnabled(true);
        privateKey.setState(KeyState.ACTIVE);
        privateKey.setKeyAlgorithm(KeyAlgorithm.RSA);
        cryptographicKeyItemRepository.save(privateKey);
        CryptographicKeyItem publicKey = new CryptographicKeyItem();
        publicKey.setKey(cryptographicKey);
        publicKey.setType(KeyType.PUBLIC_KEY);
        publicKey.setEnabled(true);
        publicKey.setState(KeyState.ACTIVE);
        publicKey.setKeyAlgorithm(KeyAlgorithm.RSA);
        cryptographicKeyItemRepository.save(publicKey);

        return cryptographicKey;
    }

    @Test
    void testRevokeCertificate_validationFail() {
        Assertions.assertThrows(NotFoundException.class, () -> clientOperationService.revokeCertificateAction(UUID.randomUUID(), null, true));
    }
}

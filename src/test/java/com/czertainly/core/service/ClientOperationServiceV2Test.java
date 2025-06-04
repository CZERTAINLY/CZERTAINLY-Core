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
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateTestUtil;
import com.czertainly.core.util.CertificateUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

        String csrWithAltKey = "MIId0TCCHToCAQAwETEPMA0GA1UEAxMGaHlicmlkMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrudI/rhH/R6wFrfuLic1eljFY/qwJSeMDiLjOhaS4YFgE/OKicG8nmptB3b4xWL5yrAB4tu/Nyg6YeABrNWTIhEEPay7AkmHPBa8HwpecFaxZiZMwdnI+/q3XKZD7IuDZLMbDoCfi7U8yZTX4+/GIi1M7umLfspjgLpDX+Fux/wIDAQABoIIcfjAUBgNVHUkxDTALBglghkgBZQMEAxMwggo/BgNVHUgxggo2MIIKMjALBglghkgBZQMEAxMDggohAKkJ6t8r1CaeNd4RYIUx51ocwf005ErPhK2HBMWVQv2Avp5x7nGNL2Iwhj1bbzpprVAVrtnpKafplrtypBCRXXsE8ERU1zzPdACPng5dy1zNoE8K9kHK0U1cQ2yJcwrdXDrJ7LyiUz5YBSEV6Ke5Q/lxzLLJIhUEZ0zKd1AUQuvKGlG8SYarwzQdXlDaDCqC+5gXckn+0cMyOezZmHKjqffthBShQy2uvT481ieSDO37VkHZeAC12kZo+ojzCL4bmQRSSq2HczWnqG1OqkDoIctyGYNWS9wBm49DD7wEPS6a3UmETA95dFhpvC/MwaNa0Ejhyyb2Q/XD8eBBXnY+JTutEzpzJZAWvT2Rg2U7U9nWEIpocqopBCDgLwYM8hy/Mo/vRYYJNHMgZ1KeNISVOqDu+6IySyhmu82jImVTYilGn1e4oMdf0Sz2mPXHJhBjIy/DWilnHdRKmJuIFm9zvsIRxVblR6VA5T+y+pkbZuyadtq045872cCVfR/KX4I+7d8cF4i0ktL6nQDe+wTR7jY9pXRXgDD7V+jWO/4TZNccB3GKltGlJ9VYIkVCYH63Sfv6bs7WDFMxZ/pHyoWoeJ6AFKPIEooNgo+ZLMf2Cce+N9qH84SOJagzrHLA8JwHw4KDaoNnD6oKxdtvLO+7kh+nDxyYxIkZLr0Rjk7KE/RfvHPAw/TqxN9gu/Pl2lYDjdmo4rIK0lWfEVisbfNZMd3u10n6TqvoJr3AsKivXGHlzaJeOBMpWxCcCv8tXAGch+/dPVEBOtFN4u6LjKvoKxiX2Ctsi9XWhCfKsfhWazKtoGl55D4QT66eiYy6GKoUic3WNZ+BkX769C8w12ftI3ZhzfwvXdbXdePvT00P5QeULUl/8AOOKXF1XD+wDkLu6IchB4hr52KLnKItWsipBwEmfnw/uKWMUrH/clPV6jJRHYOxoEYq7zpfjiWOPUeQZHI19oD8+9B6dld0imFPNCU3jfjszcTiWg3OyhmO5+0U0dVQQtDED0OzF0OXIdPRmoFqBwgCCQ8Gw9pVBKhuhnkRkxZwlmDpuviJdpP0jLuUPtTXeCe52lyWf+Roh6X4hfiQ0EdfLBsTXuVLZbj96GIY0qIdqCiFaZdXUTdILKgWnCJmMQbESmTDm/LSTGwCzH/QsoSDJu3cpOR3KGrEbkgFYJ1Ce8HFH3PGlr3AKqlaEPrX9iV4oRmog6dpIIIGDG3PoSvwHtjm5LKsDr9IXWK8yYHTqzPYZ7YPnAstIHc3+8DKX/tEbhny98AeYksdV97VwVPDHT5dy5sW7kxh+CJ2uuf6JsrVO9iWn8xSVKqsLJFru8Cir/Ojuug5bjl9N3PxZ4T9GXdpnuXLp25mhFEme0HRxByWlwncr8M6JBailvBbKhutVv4F/lfItN2fzB9JEmDNm9J+q7dP9Fjfo08oy5zBBAP0SUcf3clJsYLCh71F9k/dy/pESHEOz4GhWFlZctFXeC420wsppSGa8Ydus1QZYc8FNxudEe0fLyEyH4yS2r2TJ7zajh3VMq3aKNq20MxwFCTHZqdwIhS2E0nLGcFTUsPcJs3aiYW7N4JAKZBVXJWCjESsv7C7EXn7/OZ47n1EK9WnLVuurIXcV4nqbGRubPStYeSJtRFQdfFg70xLtIbJYss70cf4J60lGTcOBzto5HVP4JMjCfyr8VWWK1saYZTB3zvUfIlqiFN1Fd6J8zFhM8XIZhWpqBHV+4HdC7YjKKgTA58SDqmvmmElCQlswZqAno5zQZT1hWCfikJtYQoFfp8tEw0fzhDH8CPxY1wuL5Oirsjw/lmFbythBvSZ4gt3DgOTSxUoMxYIk7hKB66s/vfQF8Q6oP/IjEmI3+fP+IZjUSkgkTA1prBAxKCJoWxLkKrvp76saB2KYHd63TefDZUCGsiI57pfKHUVdLp4gkce3Uj2gB0U4xmRa5Qw53S3TKpr8sovgA8H1e6OavpnipB2X77CQ0k1NgoKvoFsSrhXbTvQIIoKJvHMU7LeANWzrJf+OUHJri8C8pK2Miotjk41s3SeFH7cNn+gIEgXetgtne9BVLnfWpUtJwYCKBjTFuZ4035NZRsgZckynzE6WTPGE9C4eCb/jU2QYTSGM42dBh7PiEAlrDAtPh6zrg5xQZncNunKSiieok8HeRqkGWHmor1frwGMoscJylZ2QnW3+WfRcJlr7TCdb0lvL1zTjMnNe5qz3MZfRz/kgTcalWPcZmu/xEFTlfKjxTmyzEChSHdNc3wv10N7XHmXu6uLyI2LdyXVNcmvJDlAMopWGNauK8qfchrfNSrvsztWmPClEZGiBgrWnuyap3DsIqMrn0BRYCLk0UE3W6v3Ggfc+bzS57+8y2+9JIioJHhAyhcSkYWj/a3FgcCEVwkUkcH6ey6eCR+GKivKoMkCfHfIszeZya+4voh4O0HZLUVciL/Oyovmqc2kT8gotlUXd67NGybWBRdIVvj9sfcPQzVLei8tvdIOKIGn33iQGRcXSS7gzwBK+nUouXcsLBWRChsIMJ6mbFzqujEQe3U1BMCeWLIpWcDBfL0HXUzDvlTcVj2vHNjn2xCq+QsqvWbb8E/39cQmFkCXpXM27vcyYrKiI4YuHJvawzI1XobBhDGYWiEyk7a13ZVmobehRaUelBr7NxDWYzQtrmUzPqgtYuNgd4SYV8zjKb9BGP+UJFansHPA8ehZ/Xd5uvGD1lO35i4hm/ZQdtycrELE8lk/ABJ29RVFI40XCrpYFW4XLSeoeWn+M1dsrBI0oL0EFfUt7CxMTt4qKO88QIUkqkL2Kqk1dPPyMtATQ1xBq+G87JCp/npoOkleQ0DBdZsHRT2HuES6uEb1PGySmx1UVEjNd+2V73Gh8HhOl1CDXSYTbG4SQ6YahGTNcQDgef6LosXXwX9bb8LzESYF4pJAc27bVCN0e7eVjSh3R25vHbgE10NShzjLnjJNrr6zFmEtDC4eVesazHDDena5K4NIUImWH8B7B8TxY9C1RwMYszN9TnZHLiaqd19suFsFoRBC0KZdunsmgNkgpiBZQY9nrlIgqzOrgJG4bkP5EX9bqVA0859UZhZl7JhAk3B55u/k3fP6hm44gXq7RsulpuWv9hxlfscfFwUlJwvi4n6fq/7c7RzMgz+uj88kKgvuDUuVHmiH/9x/esuO+SZhulcty7tOjKZWmOZ/fvhZK78kqspsJBpExx/3PpcScwCz9+9Y5EwKR4IQmeAX0+DDIJuZbSBCxfk/NT7HUie1YstXdRic5qTtY89h0UrOD+4zG9zJSAk4vf17LKaAd51C1/VqWZ9jDY0PhD4ObsgLRTray0yep0ginB/iximZqwczvvaU8fbDeMpr+ou6WZRmKs3x0h6BPZkqloFHGbBde8iRlUTLo0wj1PiV1SJIeb2EKEr7Oo7ZnroUUIwFmUq5krHkGqhgSJ5QM/EBA3n4i9Hg5zCCEiEGA1UdSjGCEhgDghIUAIfc12OaXirj1VvS4v7s3M0Nt2133beEmmX3hi91hlJWMYzMj8pvNROQIYUqpwy1SS6z1YRYvIU8iPvNqfpxvJ2Lk+quBQKQwkQgXiSmh2ijPOAoABDdVa7gSin90eyBkFFVEGogWaOozfzQMntOq5eBLoSQOboqtomSznD2WyYOdAvSotj4Y1+ykrwSn+CCSQecWBT0CBzGVakUJbTOoSJ/ya5Kpjb+Sq2QUV7qzUR0TeYWpWZo1Z8b35+wF/fL0oJyTyxj7gxbb1lMA7xR9uutlz4Ju3WCGczP/5XyB2PA+i69WZfmzqagvQxv7gAE+mU5MwHiJ7QDRyMW4E8ZiOLbsbaK3BgHd6I1G1SxigtfQh48BqHd7Gs3zfiSZLTVXSF8OUM9sgQqkON2hUH7GJ5eWIsn274914sEAU8DyZWGmdgYE8P4Ucibft9akZAtZ5XQ7YaTaHkgobU9VfoSib9nbQsWMhMN6gFEapW1L8dgUMvndBh4hLstD+lDCkTFQq8ZTMzwjjy2BIbBoia8kq2cr3stOdJqdJinCwr1PPWgNGXP0fcCDur5vyMuixTbUHhMuFtPp0fvPs3RKdiHm1bGho5sZWSwkGEP9nLe3ZF905ySmkEpmZWU5Qdy/TUyt8q5esXRIm/OkX0x3z5UHwsT8WVIIv7ulkwHcdRCZ4G9fuiKjT3jOEs3qhR79AEOddVfxMBglcrdN9YBQUliua70A7ddOxF1b84BziHhBdxmBqwh8ppvyOpH+hCoB1WLUrEJ1lUKLJlWhe0Cny0HRiu3g+Py1NoOW8ic5EU26V62VthSUHPhnRHkwxqOPMCE+tbDkrqY34O1Zn2wXMdbVxyBVqfbZ/g3RRz0Kxa1i/AtHjFChWL4Tmejv/CzyE+abuHUiyUNLsgPFrIajgX5SqsURqeYwqD2YRT0ogtZiB/bp5AQ5FcT1PdKZYjFcwQCY4kl+Qxg6K0P0I/j49rlNpgy00LNbKJiTDX+trb9yebXwAkcmqnNsB4ah4wP9W/trpZepst8wo0rPk/cH4VN3EovnSwP2jNIjYW/BDs2kQ8Gucvs6dyg8cZCLY2QYLIkroBl/7WrFL/a/Zqyd2PoYtvXmq8+aB7Je0ZN9mgVtzHSFvhAvHsQwguO06snMf2QQqL4Qj6ysvmyGSdiFEIYt2PviVLTkdOcofM6KL7YdqYvkAw/jyINH7OC+rtTIn0ux2wE167jWhBg6eaPyB1wf/QOyw3Kwri50uW7MzgdMnWRFaBp++d5HLKO+001amxu1wyUTQmqUCzKxebNZLler7Blt66Sy5jVOkCEy9tMr7QstKWWN6sI4kmHpgX9ClAyCNYHrjrVmWlSPercPF3+h20Z440CUKWyoei9+8/MNPOTYXwqNdJJIr+VJvIgeajUjq7zPeUG0tWmC3nxVnhrV8mNsx4/PlfQbrBhGWlwQ5pRjU/kAtYl2TyAnK7+HZb1CERHsLYHkEWnvAld41DmE4Bwv5pThDTiTEmSPvSy446AeSg/fhIxPBZbttZ8ynpFqVwXYsRJI9RBpJW3YriFrzQDSi4VJ9mj/U95Je57NCmQzRJtOgX3xvmU41feeYAf4q6Mpkn/PKeWs3tZN/7YywFpdQ9CHep6WRcUjfIU63l6viZPWbDtogCfUKHoGvrwSrcQn3PYrpvG8K/XUma1L3kAkYaHD4PtP16wSyANAxDyDj91K9yUoNrly9UHnVfjRFjufhHRH9yy4u3LmqmyJ+M3WUR0txjwylKDA6CtYEUgXXW9aOOY+YC0Fz5UIVOIquPwRpzhLcm6/Pi2vcjLGu7OBWVsXIegBvTj7XtQW5xY+IXc3u9773BfEP6SOlH0ICtcOaJDSJs8H0DndPGRK2oIm1/it52WBKQh9LxbL85q/gBV3J78AyBjtdj87aiGuNYhjxxroi+DXNde/Gj91FkPrOzD/NO+HYfxr4nA5dXfJJ5qEjC1YYjHyo98wM0Xf4MZ/00k2PPHKWiZWOA6WC4wCWjjGOmbhjjZuJtmmtMQes1O2G7a+EXnxRn0bkpl3d+natP/2F2suaY5p6XqxGmNLS9T3s55+q+G07DjH8eR1Z3zyb1TqPGF4B9n3vJ5dKSokw7HisQ0a9KIReXO/ygBMkJqh6rJteJYTVCGylrImgKiPU2liEDd6qClHzriIimspYu/FCC+RszJrDiryKxX3U+Ch7Z9KeFjP5Uyy7fHtcutqlWBYPxfl77cf8zxWvVFQ0N3N7bKyZERlQ4ED4CSA756FFBcm1RgcFwYoowR5+mHixUhaZkZj7MrotT0PpxlPuCkmV4FciDW4fyPOgo3PuJhGRIrJhZmYKdIxtr1xR+6aakRTmEL+1tbaOhnDOgFC55kLUalQN6j3xB0TtB2HFBsNPmBGcZeM/gE9EWLfh7QRYeQpWR6Ml6DNx6KryodnwqnJv8bEA5hYjOnjbxzKuMMomIpgRvdUZd9L/fREBD2HwpgHpUJSRylYiJ79W+NkB+iltjrJUqiXhL3qihS5ah3UqYw7jkb/Gcg/v2YbI+l+uA0buq8TVyfINiCskJw7J+F10Bl/nXz+W1fNbZl1Y651Mfa4NDab8Lql2dMIcrPVTyt4Fq2nh7x5me0CHe2nycczPc6m+mq140YSeZ96bKELWb6Z5fNhUnvxt3J8AsmhBEDpsth9rCFyDqNVKEWR9wNu3rlXuw2Sq8YSh4Zq5RCKiqBwRylQs7CFA9zyfOUH1sNZBzZNT2weI0CSsNSou5VKf8hcsmym+/FPY8+xMupvVznmTS6ORtZwi/ApTyY+nRXp3LADYzCS2rfoMUXR0D2ah4d034LCMSmVhFXuzfgbG5PFLOBFtGxwuy2HDJtIDEFMSwu7nODbdUdb/9/MEJTYOKicheRBCWxbjx8UfOhcFx4mNwUub8F4YZCX9qSwlJJzRHXbkGQKbz/ZMsXiT755CVhd1YC0HctcJmy7i2E5dKz5D4Dhfw2VR7tvs4SozrY8+DlQkQa0VdEtC/Oqc11l3IROnOWgZCPrC7eBmq3ZU+nNE4vtldaQEOjnDb0qBDlP9rYlI9tvxUFLSURugk6UyZFPl7aso7KyX4cN5pPDUEPb5s+3/omwDTeSN7DIXJaoE8nOkNnjcwT/U/LDHaCRQCfJRP0aSOcq5T8NqTmO4dOaPd9VZHidMcwjtlki/2midlsXPZBpln2F1CROT3RN9y7AE8SGbn1BvdE3tjofLmG6pxXR4XhRk+kxKsEYuShUIgHtRCtkylO0oh9/hMvPRBpN6G8biDZI0lcP4ZLg9EQSQo3kIMU9786cNnIlhPxGhvihGYA1+pZI1GkyirLgRVoNeqJ43eeSPafCUUKbLcrcwEgmFYKeoXRkGcocodROvv7jvcEJf71cT4+pqEBCI6qIfeOFmnAmvd5Lfg8u9yWLTTPSzTu+Tvgsm/5PKnf9By2bZsc3L/t+oAgV5/Rru5aOVXWUFmVNmg1BgDKz9LmLR3WRxwWtRx/EbeeIy0FviwRFdcgQNZT5Bk/bHy7kH1SkuNrSh4ne79A2sChYQ4pt057bEzmSkDTf+0Ij6jING8QuITtfVUJ1myIyVcjWbDSzPz1ojOox7LtEU21FhlzO/HssNk4zv8Vlly186p7Tud889tokT8n758wG3kVpsH8FvmMtSdvVvGs7g8AYY7FtAjWX688+xid8pOYCnh0ky3/uiFmZ2Wi7BW9ueG6ziqLJR0VuP2IHL8M9ifKJEXB48JUMDXNou9Qsphu95lypAGqsEbVPbyMY10+ZCLY5VExaSty/tNzsd+FwvZbdYspm7fLcbdWNi64eQrk6hxykULcL5sEX5+YYMjszZYSeAwOdbRHNW9nT4q0Zb/Sfp7lBnZSSNtOb/ZzKXqO+JFt+sqWdYRw5B7CtNWbcgsQNGLHSi5ilhzLXJvuU4Zg5XwfVvo3SyoOm28+ubmE8URL/MwUKq4JypSKzzGgbcKLsXGZPot8+5DKgsyLacRlQ756N9hpC3OB09NqQrmXT6tpVU7xvK6Hr/rUxGGIdBiaRFO6tZEEbNzPIalEaSERUjyetZDfJCSxd5qzp2LQXOJToR+hY2zqrOkS9FNfWSZH26k6iIrYCo3uvBGK6U7bXPCwsGoi8gMvJKPou+x+zpJues8yqosfyGxCGhSjIP3O5sG83JF7f4+pVxfUVBF41f79yxxrFBOyo+/yxtqq9u3qv9RkHURjlpoQdb9kAY6R+t8kcBT85M+Y+hNE3eBytpjXXe4Z7PCRoXutA7a6UlSpZzLXidvxZXbs9m+V9CM+ojmZ/hX0iRPPV5kM9S1ECgp0F4iOBktgYwQlMfDAI90Q+5ubkENfuBqLK3sVvcz/HIBeVuutfjz+G30c61QzQVmAcz1+NlEeWVcQ9DLBTwkRwh+W2gkxFWbuFm8fSOthcJfcl5GuwO9wLYFSEfllM8ZbHm1mM2B+LAFe5gLBuB9vzyV1S3+S0P04M/OLdL/2sc+dW7cLHcf4zdid6aOzqYWShR7HuNz96QsTklz+TiUuRrDwRtAYAu591EylIJFSpNuHkBDKk1+6NVGRVyhbwM4yq16jPTWw6L/jqkFM468EeyWdh7bN4fX96XIKmQH2l7ZI33aWPHh6z+6bUausRVX+jTnzCdgUOl3FWhI3wCdgZDZ34oRi7ojhflgSLjcKnKYZ5VH4eZaFvKJe5xbqv6ZMV+G4rWcGT4rLfGkKKKgCKD4w0rX8BA6Lje4BkIvBQ+F35tnKKLrkugDKIPBld2HK9TY9PGErVwcNSsTbjj4octvnj0yQyFvxdyLuwK4K+gvqYzH1krysbUNuvFtubxceGfVmo0p7XVa1EeXbja3xHdbcMzNsr6VSm3xNmFypnZZkClGYCziURBjJkLdsyD5MwdCeVFwhv3hYqdLe7mI8EMyhCdzET8ximA7zuYUkqx1ICDquLhCfdTH3tuzoL1t2vUhA+VUnt/Upcm1V4J2gt1T4rcftLB4q5i1SqBPUJ5RovAvwVZP4rn0bGau+H/yDPjNJ+j+7Gxw+iCE+jGhXNWsm2dzzTl+nPZ0RM7f6kNXMP+YmPHoSECpioqv+D8tYY41+aCqXxYL3eZ8Idjz8NlN/vTkKhuvOLQ/nRjpRu8Yc1r89nLuH4K+CO7yhz9ACEa9CZISjNz/gql1nm41eB14CRVaLr8iUS7WvR6XNyyNjU3B3eyUB+rJnnmViDy1/XCrdGRBkhvMPDLzattR9aE+OclSNJa7zg1kfjBw2svZ3PY7hTtQqRZQjiefF+YvsLqegnBMVfICOJYm+CN2xdaLoUfxf72bp1T+KXies45HD2o5T692WXhrqpQFech6d4HgXWnsDZElC+B2Ku2SWtM2kvPtQoqC2un21H9w3vhDIxoh4d0VQs3nmLwzKkcQsRmSlpMXv+3cCDSz96GVGkICtszylqaW+Fli0LhF+xQBAPS75XMhcphHM1R3HyRAzotQsjt5TbGjL5uQpnYSRvlSVPTjrECZKXNkdZJNrI5FFwOa0RDsCdiRKWERWecnHPxzRSgMF9hL/E35+mwxYEpRwHKG64yJQWc2iUqhyqDPKFCJkmyfSY9zjU4U5U7N+21esjAB15cjJ5kzzZJR++dnJ+fjceMdYXreLai5oD2N+Q8kU7poARG/CH7RV7QBaSu+Ma5MazhWY+JuICfa+NUhg/rlLdzMvEdNZ87ae5q3eI/AvAKciV9LrvSU63nPD4Vo9mhalLvmFYRiyHOFJRTu9iSEHVr2wmtLyOGtHF/Ttx1lyoy176V+uaqDDjqvdf/OrPe6nhEvMCFi2XcEfhlG+skCGvC6hsHfdm6riFuMfGZN2CcW7PUO0+ocbhSPo7cgDHugN0H+4X6HkIAvKdCqXCCpVGN3A3xQCoksG0mq6MxO8MerMxsfesduFT/UAkUUUnlrATb4g5n+FQp3k2JA4wUhd1FDzNCuzeiw5SNsP4ELe9PW4l+7eVqfqeqgF93K+5CFrp8UeboOvcHAqdfdAxGXsJ05g7OIxyLGtvYKoGoTgIUdoZpG5sUwTLu3PK1zicnTF7vWrFTZNToax8vouVnevy8/W5xiKi5GkrLLEY6L9ewRqkabb4OIzNJG6809TapGrrcv5AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACBAYGxwjKDAwDQYJKoZIhvcNAQEEBQADgYEAM89Ei5//Qkn+TEyHM3gIJoCUtjr0oV8INCi9Va3baqSqcTlcGg+lVLU3v6ml98yX0mE4zsqjYe0npUERzNVfrW4IZQ7JccHBt3b7wC/207LsYXr1lfuqN+HFySClBp6DnFMVYznmPoY34pmwRTOG3Kjvnw1T4V2CN9MXsjwmYos=";
        request.setRequest(csrWithAltKey);
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
        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes"))
                .willReturn(WireMock.okJson("[]")));
        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/issue/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        CryptographicKey key = createCryptographicKey(null);
        CryptographicKey altKey = createCryptographicKey(null);
        CertificateContent content = new CertificateContent();
        X509Certificate x509Certificate = CertificateTestUtil.createHybridCertificate();
        content.setContent(Base64.getEncoder().encodeToString(x509Certificate.getEncoded()));
        certificateContentRepository.save(content);
        certificate.setCertificateContent(content);
        certificate.setHybridCertificate(true);
        certificate.setAltKeyUuid(createCryptographicKey(null).getUuid());
        certificateRepository.save(certificate);

        ClientCertificateRekeyRequestDto request = new ClientCertificateRekeyRequestDto();
        request.setFormat(CertificateRequestFormat.PKCS10);
        request.setKeyUuid(key.getUuid());
        request.setTokenProfileUuid(key.getTokenProfileUuid());
        request.setSignatureAttributes(List.of());
        request.setAltKeyUuid(altKey.getUuid());
        request.setAltTokenProfileUuid(altKey.getTokenProfileUuid());
        request.setAltSignatureAttributes(List.of());
        Mockito.when(cryptographicOperationService.generateCsr(eq(key.getUuid()), eq(key.getTokenProfileUuid()), any(), anyList(), any(), eq(altKey.getTokenProfileUuid()), anyList())).thenReturn("MIIBUjCBvAIBADATMREwDwYDVQQDDAhuZXdfY2VydDCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEA52WsWllsOi/XtK8VcKHN63Mhk6awMboP9iuwgtPXzkFLV/wILHH+YPAJcS8dP037SZQlAng9dF+IoLHn7WFYmQqqgkObWoH1+5LxHjkPRRNPJLKPtxfM/V+IafsddK7a5TiVD+PiKjoWQaGHVEieozV1fK2BfqVbenKbYMupGVkCAwEAAaAAMA0GCSqGSIb3DQEBBAUAA4GBALtgmv31dFCSO+KnXWeaGEVr2H8g6O0D/RS8xoTRF4yHIgU84EXL5ZWUxhLF6mAXP1de0IfeEf95gGrU9FQ7tdUnwfsBZCIhHOQ/PdzVhRRhaVaPK8N+/g1GyXM/mC074u8y+VoyhHTqAlnbGwzyJkLnVwJ0/jLiRaTdvn7zFDWr");
        SecuredParentUUID authorityUuid = authorityInstanceReference.getSecuredParentUuid();
        SecuredUUID raProfileUuid = raProfile.getSecuredUuid();
        String certificateUuid = String.valueOf(certificate.getUuid());
        Assertions.assertDoesNotThrow(() -> clientOperationService.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, request));

        certificate.setAltKeyUuid(null);
        certificateRepository.save(certificate);
        Assertions.assertDoesNotThrow(() ->  clientOperationService.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, request));

        certificate.setAltKeyUuid(altKey.getUuid());
        certificateRepository.save(certificate);
        Assertions.assertThrows(ValidationException.class, () ->  clientOperationService.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, request));

        certificate.setAltKeyUuid(null);
        certificateRepository.save(certificate);
        String fingerprint = CertificateUtil.getThumbprint(CertificateUtil.getAltPublicKey(x509Certificate.getExtensionValue(Extension.subjectAltPublicKeyInfo.getId())).getEncoded());
        request.setAltKeyUuid(createCryptographicKey(fingerprint).getUuid());
        Assertions.assertThrows(ValidationException.class, () ->  clientOperationService.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, request));

        request.setAltKeyUuid(null);
        Assertions.assertThrows(ValidationException.class, () ->  clientOperationService.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, request));

        certificate.setHybridCertificate(false);
        request.setAltKeyUuid(altKey.getUuid());
        certificateRepository.save(certificate);
        Assertions.assertDoesNotThrow(() ->  clientOperationService.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, request));
    }

    private CryptographicKey createCryptographicKey(String fingerprint) {
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
        publicKey.setFingerprint(fingerprint);
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

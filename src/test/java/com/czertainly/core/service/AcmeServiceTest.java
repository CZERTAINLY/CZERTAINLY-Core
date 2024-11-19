package com.czertainly.core.service;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.acme.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.acme.*;
import com.czertainly.core.service.acme.AcmeConstants;
import com.czertainly.core.service.acme.AcmeService;
import com.czertainly.core.util.AcmeCommonHelper;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class AcmeServiceTest extends BaseSpringBootTest {

    private static final String BASE_URI = "https://localhost:8443/api/acme/";
    private static final String RA_BASE_URI = BASE_URI + "raProfiles/";
    private static final String ACME_PROFILE_NAME = "testAcmeProfile1";
    private static final String RA_PROFILE_NAME = "testRaProfile1";
    private static final String NONCE_HEADER_CUSTOM_PARAM = "nonce";
    private static final String URL_HEADER_CUSTOM_PARAM = "url";
    private static final String ACME_ACCOUNT_ID_VALID = "RMAl70zrRrs";
    private static final String ACME_ACCOUNT_ID_INVALID = "invalidAccountId";
    private static final String AUTHORIZATION_ID_PENDING = "auth123";
    private static final String ORDER_ID_VALID = "order123";

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private AcmeNonceRepository acmeNonceRepository;

    @Autowired
    private AcmeAccountRepository acmeAccountRepository;

    @Autowired
    private AcmeProfileRepository acmeProfileRepository;

    @Autowired
    private AcmeOrderRepository acmeOrderRepository;

    @Autowired
    private AcmeAuthorizationRepository acmeAuthorizationRepository;

    @Autowired
    private AcmeChallengeRepository acmeChallengeRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private AcmeService acmeService;

    private AcmeNonce acmeValidNonce;
    private JWSSigner rsa2048Signer;
    private RSAKey rsa2048PublicJWK;
    private JWSSigner newRsa2048Signer;
    private RSAKey newRsa2048PublicJWK;
    private String b64UrlCertificate;
    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() throws JOSEException, NoSuchAlgorithmException, CertificateException, SignatureException, InvalidKeyException, NoSuchProviderException, OperatorCreationException {
        // prepare mock server
        mockServer = new WireMockServer(0);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        mockServer.stubFor(WireMock
                .post(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/revoke/attributes/validate"))
                .willReturn(WireMock.okJson("true")));

        mockServer.stubFor(WireMock
                .get(WireMock.urlPathMatching("/v2/authorityProvider/authorities/[^/]+/certificates/revoke/attributes"))
                .willReturn(WireMock.okJson("[]")));

        RSAKey rsa2048JWK = new RSAKeyGenerator(2048)
                .generate();
        rsa2048PublicJWK = rsa2048JWK.toPublicJWK();
        rsa2048Signer = new RSASSASigner(rsa2048JWK);

        RSAKey newRsa2048JWK = new RSAKeyGenerator(2048)
                .generate();
        newRsa2048PublicJWK = newRsa2048JWK.toPublicJWK();
        newRsa2048Signer = new RSASSASigner(newRsa2048JWK);

        KeyPair keyPair = rsa2048JWK.toKeyPair();
        X509Certificate x509Certificate = CertificateUtil.generateRandomX509Certificate(keyPair);
        String b64Certificate = Base64.getEncoder().encodeToString(x509Certificate.getEncoded());
        b64UrlCertificate = Base64.getUrlEncoder().encodeToString(x509Certificate.getEncoded());

        Connector connector = new Connector();
        connector.setUrl("http://localhost:" + mockServer.port());
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        RaProfile raProfile = new RaProfile();
        raProfile.setEnabled(true);
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile = raProfileRepository.save(raProfile);

        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent(b64Certificate);
        certificateContent = certificateContentRepository.save(certificateContent);

        Certificate certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate.setRaProfile(raProfile);
        certificate = certificateRepository.save(certificate);

        certificateContent.setCertificate(certificate);
        certificateContentRepository.save(certificateContent);

        AcmeProfile acmeProfile = new AcmeProfile();
        acmeProfile.setRaProfile(raProfile);
        acmeProfile.setWebsite("sample website");
        acmeProfile.setTermsOfServiceUrl("sample terms");
        acmeProfile.setValidity(30);
        acmeProfile.setRetryInterval(30);
        acmeProfile.setDescription("sample description");
        acmeProfile.setName(ACME_PROFILE_NAME);
        acmeProfile.setDnsResolverPort("53");
        acmeProfile.setDnsResolverIp("localhost");
        acmeProfile.setTermsOfServiceChangeUrl("change url");
        acmeProfile.setEnabled(true);
        acmeProfile.setDisableNewOrders(false);
        acmeProfileRepository.save(acmeProfile);

        raProfile.setAcmeProfile(acmeProfile);
        raProfileRepository.save(raProfile);

        AcmeAccount acmeAccount = new AcmeAccount();
        acmeAccount.setStatus(AccountStatus.VALID);
        acmeAccount.setEnabled(true);
        acmeAccount.setAccountId(ACME_ACCOUNT_ID_VALID);
        acmeAccount.setTermsOfServiceAgreed(true);
        acmeAccount.setAcmeProfile(acmeProfile);
        acmeAccount.setRaProfile(raProfile);
        acmeAccount.setPublicKey(Base64.getEncoder().encodeToString(rsa2048JWK.toPublicKey().getEncoded()));
        acmeAccountRepository.save(acmeAccount);

        AcmeOrder order1 = new AcmeOrder();
        order1.setOrderId(ORDER_ID_VALID);
        order1.setStatus(OrderStatus.VALID);
        order1.setAcmeAccount(acmeAccount);
        acmeOrderRepository.save(order1);

        AcmeAuthorization authorization1 = new AcmeAuthorization();
        authorization1.setAuthorizationId(AUTHORIZATION_ID_PENDING);
        authorization1.setStatus(AuthorizationStatus.PENDING);
        authorization1.setWildcard(false);
        authorization1.setOrderUuid(order1.getUuid());
        acmeAuthorizationRepository.save(authorization1);

        AcmeChallenge challenge2 = new AcmeChallenge();
        challenge2.setChallengeId("challenge123");
        challenge2.setStatus(ChallengeStatus.VALID);
        challenge2.setType(ChallengeType.HTTP01);
        challenge2.setToken("122324");
        challenge2.setAuthorizationUuid(authorization1.getUuid());
        acmeChallengeRepository.save(challenge2);

        Date expires = AcmeCommonHelper.addSeconds(new Date(), AcmeConstants.NONCE_VALIDITY);
        acmeValidNonce = new AcmeNonce();
        acmeValidNonce.setNonce("5pSv1vR6SEJryGlA0JRns6e376ZGjUt-CYxmqvwBEaY");
        acmeValidNonce.setCreated(new Date());
        acmeValidNonce.setExpires(expires);
        acmeNonceRepository.save(acmeValidNonce);

    }

    @Test
    public void testGetDirectory() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(BASE_URI + ACME_PROFILE_NAME + "/directory");
        ResponseEntity<Directory> directory = acmeService.getDirectory(ACME_PROFILE_NAME, requestUri, false);
        assertGetDirectory(directory);
    }

    @Test
    public void testGetDirectory_raProfileBased() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(RA_BASE_URI + RA_PROFILE_NAME + "/directory");
        ResponseEntity<Directory> directory = acmeService.getDirectory(RA_PROFILE_NAME, requestUri, true);
        assertGetDirectory(directory);
    }

    private void assertGetDirectory(ResponseEntity<Directory> response) {
        Assertions.assertNotNull(response);
        // status code is 200
        Assertions.assertEquals(200, response.getStatusCode().value());

        Assertions.assertTrue(Objects.requireNonNull(response.getBody()).getNewOrder().endsWith("/new-order"));
        Assertions.assertTrue(response.getBody().getKeyChange().endsWith("/key-change"));
        Assertions.assertTrue(response.getBody().getNewAccount().endsWith("/new-account"));
        Assertions.assertTrue(response.getBody().getNewNonce().endsWith("/new-nonce"));
        Assertions.assertTrue(response.getBody().getRevokeCert().endsWith("/revoke-cert"));
    }

    @Test
    public void testGetNonce() {
        URI requestUri = URI.create(BASE_URI + ACME_PROFILE_NAME + "/new-nonce");
        ResponseEntity<?> response = acmeService.getNonce(ACME_PROFILE_NAME, true, requestUri, false);
        assertGetNonce(response);
    }

    @Test
    public void testGetNonce_raProfileBased() {
        URI requestUri = URI.create(RA_BASE_URI + RA_PROFILE_NAME + "/new-nonce");
        ResponseEntity<?> response = acmeService.getNonce(RA_PROFILE_NAME, true, requestUri, true);
        assertGetNonce(response);
    }

    private void assertGetNonce(ResponseEntity<?> response) {
        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        // Nonce header is present
        Assertions.assertNotNull(response.getHeaders().get(AcmeConstants.NONCE_HEADER_NAME));
    }

    @Test
    public void testNewAccount() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-account");
        ResponseEntity<Account> account = acmeService.newAccount(ACME_PROFILE_NAME, buildNewAccountRequestJSON(requestUri), requestUri, false);
        assertNewAccount(account);
    }

    @Test
    public void testNewAccount_raProfileBased() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        URI requestUri = new URI(RA_BASE_URI + RA_PROFILE_NAME + "/new-account");
        ResponseEntity<Account> account = acmeService.newAccount(RA_PROFILE_NAME, buildNewAccountRequestJSON(requestUri), requestUri, true);
        assertNewAccount(account);
    }

    private String buildNewAccountRequestJSON(URI requestUri) throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"contact\":[\"mailto:test.test@test\"],\"termsOfServiceAgreed\":true}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .jwk(rsa2048PublicJWK)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    private void assertNewAccount(ResponseEntity<Account> account) {
        // status code is 200
        Assertions.assertEquals(HttpStatus.CREATED, account.getStatusCode());

        Assertions.assertNotNull(account);
        Assertions.assertNotNull(account.getHeaders().getLocation());
        Assertions.assertEquals(AccountStatus.VALID, Objects.requireNonNull(account.getBody()).getStatus());
    }

    @Test
    public void testNewAccount_fail() throws URISyntaxException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-account");
        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(ACME_PROFILE_NAME, buildNewAccountRequestJSON_fail(), requestUri, false));
    }

    @Test
    public void testNewAccount_fail_raProfileBased() throws URISyntaxException {
        URI requestUri = new URI(RA_BASE_URI + RA_PROFILE_NAME + "/new-account");
        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(RA_PROFILE_NAME, buildNewAccountRequestJSON_fail(), requestUri, true));
    }

    private String buildNewAccountRequestJSON_fail() throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("dfgdrtyufghgjghktyfghdtu"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .jwk(rsa2048PublicJWK)
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    @Test
    public void testNewOrder() throws JOSEException, URISyntaxException, AcmeProblemDocumentException, NotFoundException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-order");
        ResponseEntity<Order> order = acmeService.newOrder(
                ACME_PROFILE_NAME,
                buildNewOrderRequestJSON(requestUri, BASE_URI + ACME_PROFILE_NAME), requestUri, false);
        assertNewOrder(order);
    }

    @Test
    public void testNewOrder_raProfileBased() throws JOSEException, URISyntaxException, AcmeProblemDocumentException, NotFoundException {
        URI requestUri = new URI(RA_BASE_URI + RA_PROFILE_NAME + "/new-order");
        ResponseEntity<Order> order = acmeService.newOrder(
                RA_PROFILE_NAME,
                buildNewOrderRequestJSON(requestUri, RA_BASE_URI + RA_PROFILE_NAME), requestUri, true);
        assertNewOrder(order);
    }

    private String buildNewOrderRequestJSON(URI requestUri, String baseUri) throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"identifiers\":[{\"type\":\"dns\",\"value\":\"debian10.acme.local\"}]}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(baseUri + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    private void assertNewOrder(ResponseEntity<Order> order) {
        // status code is 201
        Assertions.assertEquals(HttpStatus.CREATED, order.getStatusCode());

        Assertions.assertNotNull(order.getBody());
        Assertions.assertEquals(OrderStatus.PENDING, order.getBody().getStatus());
        Assertions.assertEquals(1, order.getBody().getAuthorizations().size());
    }

    @Test
    public void testNewOrder_Fail() throws URISyntaxException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-order");
        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newOrder(ACME_PROFILE_NAME, buildNewOrderRequestJSON_fail(), requestUri, false));
    }

    @Test
    public void testNewOrder_fail_raProfileBased() throws URISyntaxException {
        URI requestUri = new URI(RA_BASE_URI + RA_PROFILE_NAME + "/new-order");
        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newOrder(RA_PROFILE_NAME, buildNewOrderRequestJSON_fail(), requestUri, true));
    }

    private String buildNewOrderRequestJSON_fail() throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("dfgdrtyufghgjghktyfghdtu"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    @Test
    public void testGetAuthorization() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        String baseUri = BASE_URI + ACME_PROFILE_NAME;
        URI requestUri = new URI(baseUri + "/authz/" + AUTHORIZATION_ID_PENDING);
        ResponseEntity<Authorization> authorization = acmeService.getAuthorization(
                ACME_PROFILE_NAME, AUTHORIZATION_ID_PENDING,
                buildGetAuthorizationRequestJSON(requestUri, baseUri), requestUri, false);
        assertGetAuthorization(authorization);
    }

    @Test
    public void testGetAuthorization_raProfileBased() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        String baseUri = RA_BASE_URI + RA_PROFILE_NAME;
        URI requestUri = new URI(baseUri + "/authz/" + AUTHORIZATION_ID_PENDING);
        ResponseEntity<Authorization> authorization = acmeService.getAuthorization(
                RA_PROFILE_NAME, AUTHORIZATION_ID_PENDING,
                buildGetAuthorizationRequestJSON(requestUri, baseUri), requestUri, true);
        assertGetAuthorization(authorization);
    }

    private String buildGetAuthorizationRequestJSON(URI requestUri, String baseUri) throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload(""));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(baseUri + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    private void assertGetAuthorization(ResponseEntity<Authorization> authorization) {
        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, authorization.getStatusCode());

        Assertions.assertNotNull(authorization);
        Assertions.assertEquals(0, Objects.requireNonNull(authorization.getBody()).getChallenges().size());
        // is pending
        Assertions.assertEquals(AuthorizationStatus.PENDING, authorization.getBody().getStatus());
    }

    @Test
    public void testFinalize() throws URISyntaxException {
        String baseUri = BASE_URI + ACME_PROFILE_NAME;
        URI requestUri = new URI(baseUri + "/order/" + ORDER_ID_VALID + "/finalize");
        Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.finalizeOrder(
                ACME_PROFILE_NAME, ORDER_ID_VALID,
                buildFinalizeRequestJSON(requestUri, baseUri), requestUri, false));
    }

    @Test
    public void testFinalize_raProfileBased() throws URISyntaxException {
        String baseUri = RA_BASE_URI + RA_PROFILE_NAME;
        URI requestUri = new URI(baseUri + "/order/" + ORDER_ID_VALID + "/finalize");
        Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.finalizeOrder(
                RA_PROFILE_NAME, ORDER_ID_VALID,
                buildFinalizeRequestJSON(requestUri, baseUri), requestUri, true));
    }

    private String buildFinalizeRequestJSON(URI requestUri, String baseUri) throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"csr\":\"MIICdjCCAV4CAQIwADCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALeJvx7JWbwzobWL74KyHz0FjPqt0R5iOaOxiYqpfMY-ZVhMBkS0FqnCBQzMn5BkHukdx7HsIMkJ-sM01HVHJaRpgpf1zeTyRQjY7ESDikRL_1Ekxi6Sgf5unzB35aP2EBxiAaomG610HjpqSfGtOzEf12hy4jkcC446TT8nE9dm6CBf7XAoq9vXxXRjnAgdkr62yIzanXedDwdcNyk5EiiRWQXwW-L5Pex5808ip2gmE5Al5SPUiv8eDCq02QVDJ8Ln4UPYkxL1b6RMlfEgKLsGEZX0e-FC0w_fiBN48zrvHxqM2fdU7Ae8pRDwUOClYOxDkrvDv60RGikLlQZ45FcCAwEAAaAxMC8GCSqGSIb3DQEJDjEiMCAwHgYDVR0RBBcwFYITZGViaWFuMTAuYWNtZS5sb2NhbDANBgkqhkiG9w0BAQsFAAOCAQEAHlO0ZuPuYEtplU0gEUj88Yi1MWkrElx0JoTk7qonRsufu_Y2P_u-RrkWOzM3VJ08lNz90L_mnc8NOONMl_WlYWBywbUMsGar4Y_1x0ySOEdp5fg87rxY1b2jbSL7tPe4OV7yAebdCEzzXXBi3Ay9NoJAhwNONjyRp92vqT5-MWMXQyZvdcUMM38l6aNc9jof3EluNbgO7nWSle6MQJJvlEYwXx7ZPvvgxMfrRa-Yc_aWS7w25MSAODKKwvIivGn5q_owfd5AozYp0pymiLLbvAWhYVWL_-bGvJ13xpyfNPnGJIdwcY8zgikYPyBfbRmPyKJLPI4QnWz8GsWGiaUgjA\"}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(baseUri + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    @Test
    public void testRevokeCert_fail() throws URISyntaxException {
        String baseUri = BASE_URI + ACME_PROFILE_NAME;
        URI requestUri = new URI(baseUri + "/revoke-cert");
        Assertions.assertThrows(NullPointerException.class,
                () -> acmeService.revokeCertificate(
                        ACME_PROFILE_NAME, buildRevokeCertRequestJSON_fail(requestUri, baseUri), requestUri, false));

    }

    @Test
    public void testRevokeCert_fail_raProfileBased() throws URISyntaxException {
        String baseUri = RA_BASE_URI + RA_PROFILE_NAME;
        URI requestUri = new URI(baseUri + "/revoke-cert");
        Assertions.assertThrows(NullPointerException.class,
                () -> acmeService.revokeCertificate(
                        RA_PROFILE_NAME, buildRevokeCertRequestJSON_fail(requestUri, baseUri), requestUri, true));

    }

    private String buildRevokeCertRequestJSON_fail(URI requestUri, String baseUri) throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"certificate\":\"MIIFOzCCAyOgAwIBAgITGAAAA4HCMidgplmlrQAAAAADgTANBgkqhkiG9w0BAQ0FADA3MRcwFQYDVQQDDA5EZW1vIE1TIFN1YiBDQTEcMBoGA1UECgwTM0tleSBDb21wYW55IHMuci5vLjAeFw0yNDAzMTIxMTI3MDBaFw0yNjAzMTIxMTI3MDBaMBgxFjAUBgNVBAMTDXRmdC4za2V5LnRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDgoHj1WO_sXoqfcr7pm5KUjsAnmdjgzBbFwHCAvlDwsF8Z6B6i77AB-xLIUdcVLPu417mApEbH9Nu9jvcO_b2QEh_tDAczbug6SVMwgl7va3H5qGwg_0qepRHNWAMt1TWgY-rFZbUn1WLSO97armFjVPKK-AeuBVz4EQIqS1vxLLg0MxajR19euvkLBjbjYtjp7pwgHT2jMsccJ06bGN3Ik7wTZMnObfwxhhmwApEjyeDevVywopULc9zarvOSgFTnejlfOmwUBnnHlp8Xpq7P_izt1AhJkij9eElzSXnZUHAFQoQh3fQ6yelXFBxDOEAao8o3FR-R6Ss3kZ3mxkbjAgMBAAGjggFdMIIBWTAnBgNVHREEIDAegg10ZnQuM2tleS50ZXN0gg13d3cuM2tleS50ZXN0MB0GA1UdDgQWBBSA81KtARou26mp921nppmTGjC6BTAfBgNVHSMEGDAWgBSSwrzfVcXBk4VJB_esyR0LaAEHUTBNBgNVHR8ERjBEMEKgQKA-hjxodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2NybHMvZGVtby9EZW1vJTIwTVMlMjBTdWIlMjBDQS5jcmwwVwYIKwYBBQUHAQEESzBJMEcGCCsGAQUFBzABhjtodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2Nhcy9kZW1vL0RlbW8lMjBNUyUyMFN1YiUyMENBLmNydDAhBgkrBgEEAYI3FAIEFB4SAFcAZQBiAFMAZQByAHYAZQByMA4GA1UdDwEB_wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATANBgkqhkiG9w0BAQ0FAAOCAgEAb_N3sf9Kda5t_jsL_VQYW0OPiHD0V1QcwqiyplvclD7NahnV7QiUwS7V-QmHHD1V2_xkYNhlgkinu1SWbpJ8gAcLDbADfnMkaOZNr6dvKiDGw0Xppmfbha1Bbb3JA_DOHFrXBm3795mQDgaRuvPke0qyyL1DP9xAdxubQaYQDZA9WAYNztgVe3V4zngwzI6P6BiDQ7CgZLNv_e8e5ME4_MCeO0cUFxt7mzKIhH54wL4yY8DJ3LHVWXsMPntRMdvYWjYf-1Ivb5x2WvuU_SPcnCSyEj0qdcLlm9BWxbfM-5h4gXWvsCjG2anGLtsl5Ut3Sz1vvoM49N981pZEZDlNFlsBgYCF-MDKZwBOiX8uTgQkv5bqA7_tPvIgQI_JTbSYeqRtb4J6SH1_uRrhyU7w88PlSmZwkf5S5ZxX9eqjSEFENB7ARh4KaiHyYqTfYxAP6-EFs9dxBTQ5eQu2jFXy4xJG4g-r1KZujv6wgPoDZsbbqTfBg27_sQsTyzZqI1vL5UrCqxDSo-Pw9JPITYi8AdOffT0hkgQ7RmLHb6HYV7JqABmhZ3G9QQfuk2W7_o6l6jnpZM7pHEkZ30s54cIHgYG3JifXd2m6uxU6iX48mJy_VUZcVikxSbCg5eLlvq_HWnxk2DE_9PWjA_YxZs2Jtqpi2FtLCli2cykGGumhhJ0\",\"reason\":8}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(baseUri + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    @Test
    public void testRevokeCert_withPrivateKey() throws URISyntaxException, JOSEException, AcmeProblemDocumentException, ConnectorException, CertificateException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/revoke-cert");
        ResponseEntity<?> response = acmeService.revokeCertificate(
                ACME_PROFILE_NAME,
                buildRevokeCertRequestJSON_withPrivateKey(requestUri), requestUri, false);
        assertRevokeCert_withPrivateKey(response);
    }

    @Test
    public void testRevokeCert_withPrivateKey_raProfileBased() throws URISyntaxException, JOSEException, AcmeProblemDocumentException, ConnectorException, CertificateException {
        URI requestUri = new URI(RA_BASE_URI + RA_PROFILE_NAME + "/revoke-cert");
        ResponseEntity<?> response = acmeService.revokeCertificate(
                RA_PROFILE_NAME,
                buildRevokeCertRequestJSON_withPrivateKey(requestUri), requestUri, true);
        assertRevokeCert_withPrivateKey(response);
    }

    private String buildRevokeCertRequestJSON_withPrivateKey(URI requestUri) throws JOSEException {
        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"certificate\":\"" + b64UrlCertificate + "\",\"reason\":0}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .jwk(rsa2048PublicJWK)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    private void assertRevokeCert_withPrivateKey(ResponseEntity<?> response) {
        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void testGetOrderList() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(BASE_URI + ACME_PROFILE_NAME + "/orders/" + ACME_ACCOUNT_ID_VALID);
        ResponseEntity<List<Order>> orders = acmeService.listOrders(ACME_PROFILE_NAME, ACME_ACCOUNT_ID_VALID, requestUri, false);
        assertGetOrderList(orders);
    }

    @Test
    public void testGetOrderList_raProfileBased() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(RA_BASE_URI + RA_PROFILE_NAME + "/orders/" + ACME_ACCOUNT_ID_VALID);
        ResponseEntity<List<Order>> orders = acmeService.listOrders(RA_PROFILE_NAME, ACME_ACCOUNT_ID_VALID, requestUri, true);
        assertGetOrderList(orders);
    }

    private void assertGetOrderList(ResponseEntity<List<Order>> orders) {
        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, orders.getStatusCode());
        Assertions.assertNotNull(orders);
    }

    @Test
    public void testGetOrderListFail() {
        URI requestUri = URI.create(BASE_URI + ACME_PROFILE_NAME + "/orders/" + ACME_ACCOUNT_ID_INVALID);
        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.listOrders(ACME_PROFILE_NAME, ACME_ACCOUNT_ID_INVALID, requestUri, false));
    }

    @Test
    public void testGetOrderList_fail_isRaProfileBased() {
        URI requestUri = URI.create(RA_BASE_URI + RA_PROFILE_NAME + "/orders/" + ACME_ACCOUNT_ID_INVALID);
        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.listOrders(RA_PROFILE_NAME, ACME_ACCOUNT_ID_INVALID, requestUri, true));
    }

    @Test
    public void testGetOrder() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(BASE_URI + ACME_PROFILE_NAME + "/order/" + ORDER_ID_VALID);
        ResponseEntity<Order> orders = acmeService.getOrder(ACME_PROFILE_NAME, ORDER_ID_VALID, requestUri, false);
        assertGetOrder(orders);
    }

    @Test
    public void testGetOrder_raProfileBased() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(RA_BASE_URI + RA_PROFILE_NAME + "/order/" + ORDER_ID_VALID);
        ResponseEntity<Order> orders = acmeService.getOrder(RA_PROFILE_NAME, ORDER_ID_VALID, requestUri, true);
        assertGetOrder(orders);
    }

    private void assertGetOrder(ResponseEntity<Order> orders) {
        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, orders.getStatusCode());
        Assertions.assertNotNull(orders);
        // order status is VALID
        Assertions.assertEquals(OrderStatus.VALID, Objects.requireNonNull(orders.getBody()).getStatus());
    }

    @Test
    public void testKeyRollover() throws JOSEException, AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(BASE_URI + ACME_PROFILE_NAME + "/key-change");
        ResponseEntity<?> response = acmeService.keyRollover(
                ACME_PROFILE_NAME,
                buildKeyRolloverRequestJSON(requestUri, BASE_URI + ACME_PROFILE_NAME), requestUri, false);
        assertKeyRollover(response);
    }

    @Test
    public void testKeyRollover_raProfileBased() throws JOSEException, AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(RA_BASE_URI + RA_PROFILE_NAME + "/key-change");
        ResponseEntity<?> response = acmeService.keyRollover(
                RA_PROFILE_NAME,
                buildKeyRolloverRequestJSON(requestUri, RA_BASE_URI + RA_PROFILE_NAME), requestUri, true);
        assertKeyRollover(response);
    }

    private String buildKeyRolloverRequestJSON(URI requestUri, String baseUri) throws JOSEException {
        String account = baseUri + "/acct/" + ACME_ACCOUNT_ID_VALID;
        String oldKey = rsa2048PublicJWK.toString();

        JWSObjectJSON innerJwsObjectJSON = new JWSObjectJSON(new Payload("{\"account\":\"" + account + "\",\"oldKey\":" + oldKey + "}"));
        innerJwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .jwk(newRsa2048PublicJWK)
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                newRsa2048Signer
        );

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload(innerJwsObjectJSON.serializeFlattened()));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(baseUri + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );
        return jwsObjectJSON.serializeFlattened();
    }

    private void assertKeyRollover(ResponseEntity<?> response) {
        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        // contains Link header
        Assertions.assertNotNull(response.getHeaders().get("Link"));
    }
}

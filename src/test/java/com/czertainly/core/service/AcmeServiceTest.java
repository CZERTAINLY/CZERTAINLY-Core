package com.czertainly.core.service;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.*;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.acme.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.acme.*;
import com.czertainly.core.service.acme.AcmeConstants;
import com.czertainly.core.service.acme.AcmeService;
import com.czertainly.core.util.AcmeCommonHelper;
import com.czertainly.core.util.BaseSpringBootTest;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class AcmeServiceTest extends BaseSpringBootTest {

    private static final String BASE_URI = "https://localhost:8443/api/acme/";
    private static final String ACME_PROFILE_NAME = "testAcmeProfile1";
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
    private AcmeService acmeService;

    private AcmeNonce acmeValidNonce;
    private JWSSigner rsa2048Signer;
    private RSAKey rsa2048PublicJWK;

    @BeforeEach
    public void setUp() throws JOSEException {

        RSAKey rsa2048JWK = new RSAKeyGenerator(2048)
                .generate();
        rsa2048PublicJWK = rsa2048JWK.toPublicJWK();
        rsa2048Signer = new RSASSASigner(rsa2048JWK);

        Connector connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector = connectorRepository.save(connector);

        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        RaProfile raProfile = new RaProfile();
        raProfile.setEnabled(true);
        raProfile.setName("testRaProfile1");
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile = raProfileRepository.save(raProfile);

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
        ResponseEntity<Directory> directory = acmeService.getDirectory(ACME_PROFILE_NAME, false);

        Assertions.assertNotNull(directory);
        // status code is 200
        Assertions.assertEquals(200, directory.getStatusCode().value());

        Assertions.assertTrue(Objects.requireNonNull(directory.getBody()).getNewOrder().endsWith("/new-order"));
        Assertions.assertTrue(directory.getBody().getKeyChange().endsWith("/key-change"));
        Assertions.assertTrue(directory.getBody().getNewAccount().endsWith("/new-account"));
        Assertions.assertTrue(directory.getBody().getNewNonce().endsWith("/new-nonce"));
        Assertions.assertTrue(directory.getBody().getRevokeCert().endsWith("/revoke-cert"));
    }

    @Test
    public void testGetNonce(){
        ResponseEntity<?> response = acmeService.getNonce(ACME_PROFILE_NAME, true);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        // Nonce header is present
        Assertions.assertNotNull(response.getHeaders().get(AcmeConstants.NONCE_HEADER_NAME));
    }

    @Test
    public void testNewAccount() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-account");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"contact\":[\"mailto:test.test@test\"],\"termsOfServiceAgreed\":true}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .jwk(rsa2048PublicJWK)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        ResponseEntity<Account> account = acmeService.newAccount(ACME_PROFILE_NAME, acmeJwsRequestJSON, requestUri, false);

        // status code is 200
        Assertions.assertEquals(HttpStatus.CREATED, account.getStatusCode());

        Assertions.assertNotNull(account);
        Assertions.assertNotNull(account.getHeaders().getLocation());
        Assertions.assertEquals(AccountStatus.VALID, Objects.requireNonNull(account.getBody()).getStatus());
    }

    @Test
    public void testNewAccount_Fail() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-account");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("dfgdrtyufghgjghktyfghdtu"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .jwk(rsa2048PublicJWK)
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(ACME_PROFILE_NAME, acmeJwsRequestJSON, requestUri, false));
    }

    @Test
    public void testNewOrder() throws JOSEException, URISyntaxException, AcmeProblemDocumentException, NotFoundException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-order");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"identifiers\":[{\"type\":\"dns\",\"value\":\"debian10.acme.local\"}]}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + ACME_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        ResponseEntity<Order> order = acmeService.newOrder(ACME_PROFILE_NAME, acmeJwsRequestJSON, requestUri, false);

        // status code is 201
        Assertions.assertEquals(HttpStatus.CREATED, order.getStatusCode());

        Assertions.assertNotNull(order.getBody());
        Assertions.assertEquals(OrderStatus.PENDING, order.getBody().getStatus());
        Assertions.assertEquals(1, order.getBody().getAuthorizations().size());
    }

    @Test
    public void testNewOrder_Fail() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/new-order");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("dfgdrtyufghgjghktyfghdtu"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newOrder(ACME_PROFILE_NAME, acmeJwsRequestJSON, requestUri, false));
    }

    @Test
    public void testGetAuthorization() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/authz/" + AUTHORIZATION_ID_PENDING);

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload(""));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + ACME_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        ResponseEntity<Authorization> authorization = acmeService.getAuthorization(ACME_PROFILE_NAME, AUTHORIZATION_ID_PENDING, acmeJwsRequestJSON, requestUri, false);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, authorization.getStatusCode());

        Assertions.assertNotNull(authorization);
        Assertions.assertEquals(0, Objects.requireNonNull(authorization.getBody()).getChallenges().size());
        // is pending
        Assertions.assertEquals(AuthorizationStatus.PENDING, authorization.getBody().getStatus());
    }

    @Test
    public void testFinalize() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/order/" + ORDER_ID_VALID + "/finalize");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"csr\":\"MIICdjCCAV4CAQIwADCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALeJvx7JWbwzobWL74KyHz0FjPqt0R5iOaOxiYqpfMY-ZVhMBkS0FqnCBQzMn5BkHukdx7HsIMkJ-sM01HVHJaRpgpf1zeTyRQjY7ESDikRL_1Ekxi6Sgf5unzB35aP2EBxiAaomG610HjpqSfGtOzEf12hy4jkcC446TT8nE9dm6CBf7XAoq9vXxXRjnAgdkr62yIzanXedDwdcNyk5EiiRWQXwW-L5Pex5808ip2gmE5Al5SPUiv8eDCq02QVDJ8Ln4UPYkxL1b6RMlfEgKLsGEZX0e-FC0w_fiBN48zrvHxqM2fdU7Ae8pRDwUOClYOxDkrvDv60RGikLlQZ45FcCAwEAAaAxMC8GCSqGSIb3DQEJDjEiMCAwHgYDVR0RBBcwFYITZGViaWFuMTAuYWNtZS5sb2NhbDANBgkqhkiG9w0BAQsFAAOCAQEAHlO0ZuPuYEtplU0gEUj88Yi1MWkrElx0JoTk7qonRsufu_Y2P_u-RrkWOzM3VJ08lNz90L_mnc8NOONMl_WlYWBywbUMsGar4Y_1x0ySOEdp5fg87rxY1b2jbSL7tPe4OV7yAebdCEzzXXBi3Ay9NoJAhwNONjyRp92vqT5-MWMXQyZvdcUMM38l6aNc9jof3EluNbgO7nWSle6MQJJvlEYwXx7ZPvvgxMfrRa-Yc_aWS7w25MSAODKKwvIivGn5q_owfd5AozYp0pymiLLbvAWhYVWL_-bGvJ13xpyfNPnGJIdwcY8zgikYPyBfbRmPyKJLPI4QnWz8GsWGiaUgjA\"}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + ACME_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.finalizeOrder(ACME_PROFILE_NAME, ORDER_ID_VALID, acmeJwsRequestJSON, requestUri, false));
    }

    @Test
    public void testRevokeCert() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + ACME_PROFILE_NAME + "/revoke-cert");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"certificate\":\"MIIFVTCCAz2gAwIBAgITGAAAAUdDco1tiD3taAAAAAABRzANBgkqhkiG9w0BAQ0FADA3MRcwFQYDVQQDDA5EZW1vIE1TIFN1YiBDQTEcMBoGA1UECgwTM0tleSBDb21wYW55IHMuci5vLjAeFw0yMjAxMjUwOTUzMTBaFw0yNDAxMjUwOTUzMTBaMAAwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC3ib8eyVm8M6G1i--Csh89BYz6rdEeYjmjsYmKqXzGPmVYTAZEtBapwgUMzJ-QZB7pHcex7CDJCfrDNNR1RyWkaYKX9c3k8kUI2OxEg4pES_9RJMYukoH-bp8wd-Wj9hAcYgGqJhutdB46aknxrTsxH9docuI5HAuOOk0_JxPXZuggX-1wKKvb18V0Y5wIHZK-tsiM2p13nQ8HXDcpORIokVkF8Fvi-T3sefNPIqdoJhOQJeUj1Ir_HgwqtNkFQyfC5-FD2JMS9W-kTJXxICi7BhGV9HvhQtMP34gTePM67x8ajNn3VOwHvKUQ8FDgpWDsQ5K7w7-tERopC5UGeORXAgMBAAGjggGPMIIBizAhBgNVHREBAf8EFzAVghNkZWJpYW4xMC5hY21lLmxvY2FsMB0GA1UdDgQWBBSNPQbi3utc-JrBAyZ1xeuE_qyxXDAfBgNVHSMEGDAWgBSSwrzfVcXBk4VJB_esyR0LaAEHUTBNBgNVHR8ERjBEMEKgQKA-hjxodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2NybHMvZGVtby9EZW1vJTIwTVMlMjBTdWIlMjBDQS5jcmwwVwYIKwYBBQUHAQEESzBJMEcGCCsGAQUFBzABhjtodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2Nhcy9kZW1vL0RlbW8lMjBNUyUyMFN1YiUyMENBLmNydDAOBgNVHQ8BAf8EBAMCBaAwPAYJKwYBBAGCNxUHBC8wLQYlKwYBBAGCNxUIh-WDYaiHcIKJgTyEk8IBhYi5aoFMgpHeTpOWVQIBZAIBBDATBgNVHSUEDDAKBggrBgEFBQcDATAbBgkrBgEEAYI3FQoEDjAMMAoGCCsGAQUFBwMBMA0GCSqGSIb3DQEBDQUAA4ICAQCj2UAyXkoKboLJQd2AnWDehAwLpEw9jhjSs36Jo5seRrTJRXFo4ldXyHPgMPRMNWhDb-LznRNvwkb0xzKovby5fQc8UqriMWW2TfeHHnumiABVUdf4w2jZsHbi0S20F8u9E4utamfuCeST6dyAuozyyYv3QS6nVr1k6Smr6Pv18qOfIZ37YG8-tfdgv9UQwJVvxBuPIfkN1yZA7knuq2oBqEzfPuk8j2muyTwgvA_3zaETmOQxP4Drs7mRpJB3l2nLzpuebgVlYJiHGbyagEinxvRQ6LuPg7nSbgKaqHkJVHkCI6iH_N6Y7icqpfHOJQPAHiPNmxreKIUZrWiyHDF1bJCzZZi7GFQUssjKCm26nr9hF8TbgVyrl1r2p18ApoCkvgPhkPOK0w5j1J032cOQPM6eRnderelBsWyFZOYp5ixLhyLGrIE7jxey77XfK8-QEK3-aww9sEyT5ARxbPWg45n4GzAppjq_mZ69RAu-VqRoJ3cRWM5LlQHyV4yvp7dH7MFT1Vrse51bh4NrsDppkaLNFHULra9FShaKdI0AxCJPXKZazLeix89jnJ3HFn4tXDH6BHtfsx7whnDkTnS5lD1wAtxxr5-fmIcBPxCJkFrPCXiq7208VqCank0P_BNoCY9Xpaav60a4ScN0-JBPITIpUoCDTxq3l3xL7utGnw\",\"reason\":8}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + ACME_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(NullPointerException.class,
                () -> acmeService.revokeCertificate(ACME_PROFILE_NAME, acmeJwsRequestJSON, requestUri, false));

    }

    @Test
    public void testGetOrderList() throws AcmeProblemDocumentException, NotFoundException {
        ResponseEntity<List<Order>> orders = acmeService.listOrders(ACME_PROFILE_NAME, ACME_ACCOUNT_ID_VALID);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, orders.getStatusCode());
        Assertions.assertNotNull(orders);
    }

    @Test
    public void testGetOrderListFail() {
        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.listOrders(ACME_PROFILE_NAME, ACME_ACCOUNT_ID_INVALID));
    }

    @Test
    public void testGetOrder() throws AcmeProblemDocumentException, NotFoundException {
        ResponseEntity<Order> orders = acmeService.getOrder(ACME_PROFILE_NAME, ORDER_ID_VALID);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, orders.getStatusCode());
        Assertions.assertNotNull(orders);
        // order status is VALID
        Assertions.assertEquals(OrderStatus.VALID, Objects.requireNonNull(orders.getBody()).getStatus());
    }
}

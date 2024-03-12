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

public class AcmeRaProfileServiceTest extends BaseSpringBootTest {

    private static final String BASE_URI = "https://localhost:8443/api/acme/raProfile/";
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
    private AcmeService acmeService;

    private AcmeNonce acmeValidNonce;
    private JWSSigner rsa2048Signer;
    private RSAKey rsa2048PublicJWK;
    private JWSSigner newRsa2048Signer;
    private RSAKey newRsa2048PublicJWK;

    @BeforeEach
    public void setUp() throws JOSEException {

        RSAKey rsa2048JWK = new RSAKeyGenerator(2048)
                .generate();
        rsa2048PublicJWK = rsa2048JWK.toPublicJWK();
        rsa2048Signer = new RSASSASigner(rsa2048JWK);

        RSAKey newRsa2048JWK = new RSAKeyGenerator(2048)
                .generate();
        newRsa2048PublicJWK = newRsa2048JWK.toPublicJWK();
        newRsa2048Signer = new RSASSASigner(newRsa2048JWK);

        Connector connector = new Connector();
        connector.setUrl("http://localhost:3665");
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

        AcmeProfile acmeProfile = new AcmeProfile();
        acmeProfile.setRaProfile(raProfile);
        acmeProfile.setWebsite("sample website");
        acmeProfile.setTermsOfServiceUrl("sample terms");
        acmeProfile.setValidity(30);
        acmeProfile.setRetryInterval(30);
        acmeProfile.setDescription("sample description");
        acmeProfile.setName("sameName");
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
        URI requestUri = URI.create(BASE_URI + RA_PROFILE_NAME + "/directory");

        ResponseEntity<Directory> directory = acmeService.getDirectory(RA_PROFILE_NAME, requestUri, true);

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
        URI requestUri = URI.create(BASE_URI + RA_PROFILE_NAME + "/new-nonce");

        ResponseEntity<?> response = acmeService.getNonce(RA_PROFILE_NAME, true, requestUri, true);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        // Nonce header is present
        Assertions.assertNotNull(response.getHeaders().get(AcmeConstants.NONCE_HEADER_NAME));
    }

    @Test
    public void testNewAccount() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + RA_PROFILE_NAME + "/new-account");

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

        ResponseEntity<Account> account = acmeService.newAccount(RA_PROFILE_NAME, acmeJwsRequestJSON, requestUri, true);

        // status code is 200
        Assertions.assertEquals(HttpStatus.CREATED, account.getStatusCode());

        Assertions.assertNotNull(account);
        Assertions.assertNotNull(account.getHeaders().getLocation());
        Assertions.assertEquals(AccountStatus.VALID, Objects.requireNonNull(account.getBody()).getStatus());
    }

    @Test
    public void testNewAccount_Fail() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + RA_PROFILE_NAME + "/new-account");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("dfgdrtyufghgjghktyfghdtu"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .jwk(rsa2048PublicJWK)
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(RA_PROFILE_NAME, acmeJwsRequestJSON, requestUri, true));
    }

    @Test
    public void testNewOrder() throws JOSEException, URISyntaxException, AcmeProblemDocumentException, NotFoundException {
        URI requestUri = new URI(BASE_URI + RA_PROFILE_NAME + "/new-order");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"identifiers\":[{\"type\":\"dns\",\"value\":\"debian10.acme.local\"}]}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + RA_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        ResponseEntity<Order> order = acmeService.newOrder(RA_PROFILE_NAME, acmeJwsRequestJSON, requestUri, true);

        // status code is 201
        Assertions.assertEquals(HttpStatus.CREATED, order.getStatusCode());

        Assertions.assertNotNull(order.getBody());
        Assertions.assertEquals(OrderStatus.PENDING, order.getBody().getStatus());
        Assertions.assertEquals(1, order.getBody().getAuthorizations().size());
    }

    @Test
    public void testNewOrder_Fail() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + RA_PROFILE_NAME + "/new-order");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("dfgdrtyufghgjghktyfghdtu"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newOrder(RA_PROFILE_NAME, acmeJwsRequestJSON, requestUri, true));
    }

    @Test
    public void testGetAuthorization() throws AcmeProblemDocumentException, NotFoundException, URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + RA_PROFILE_NAME + "/authz/" + AUTHORIZATION_ID_PENDING);

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload(""));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + RA_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        ResponseEntity<Authorization> authorization = acmeService.getAuthorization(RA_PROFILE_NAME, AUTHORIZATION_ID_PENDING, acmeJwsRequestJSON, requestUri, true);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, authorization.getStatusCode());

        Assertions.assertNotNull(authorization);
        Assertions.assertEquals(0, Objects.requireNonNull(authorization.getBody()).getChallenges().size());
        // is pending
        Assertions.assertEquals(AuthorizationStatus.PENDING, authorization.getBody().getStatus());
    }

    @Test
    public void testFinalize() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + RA_PROFILE_NAME + "/order/" + ORDER_ID_VALID + "/finalize");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"csr\":\"MIICdjCCAV4CAQIwADCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALeJvx7JWbwzobWL74KyHz0FjPqt0R5iOaOxiYqpfMY-ZVhMBkS0FqnCBQzMn5BkHukdx7HsIMkJ-sM01HVHJaRpgpf1zeTyRQjY7ESDikRL_1Ekxi6Sgf5unzB35aP2EBxiAaomG610HjpqSfGtOzEf12hy4jkcC446TT8nE9dm6CBf7XAoq9vXxXRjnAgdkr62yIzanXedDwdcNyk5EiiRWQXwW-L5Pex5808ip2gmE5Al5SPUiv8eDCq02QVDJ8Ln4UPYkxL1b6RMlfEgKLsGEZX0e-FC0w_fiBN48zrvHxqM2fdU7Ae8pRDwUOClYOxDkrvDv60RGikLlQZ45FcCAwEAAaAxMC8GCSqGSIb3DQEJDjEiMCAwHgYDVR0RBBcwFYITZGViaWFuMTAuYWNtZS5sb2NhbDANBgkqhkiG9w0BAQsFAAOCAQEAHlO0ZuPuYEtplU0gEUj88Yi1MWkrElx0JoTk7qonRsufu_Y2P_u-RrkWOzM3VJ08lNz90L_mnc8NOONMl_WlYWBywbUMsGar4Y_1x0ySOEdp5fg87rxY1b2jbSL7tPe4OV7yAebdCEzzXXBi3Ay9NoJAhwNONjyRp92vqT5-MWMXQyZvdcUMM38l6aNc9jof3EluNbgO7nWSle6MQJJvlEYwXx7ZPvvgxMfrRa-Yc_aWS7w25MSAODKKwvIivGn5q_owfd5AozYp0pymiLLbvAWhYVWL_-bGvJ13xpyfNPnGJIdwcY8zgikYPyBfbRmPyKJLPI4QnWz8GsWGiaUgjA\"}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + RA_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.finalizeOrder(RA_PROFILE_NAME, ORDER_ID_VALID, acmeJwsRequestJSON, requestUri, true));
    }

    @Test
    public void testRevokeCert() throws URISyntaxException, JOSEException {
        URI requestUri = new URI(BASE_URI + RA_PROFILE_NAME + "/revoke-cert");

        JWSObjectJSON jwsObjectJSON = new JWSObjectJSON(new Payload("{\"certificate\":\"MIIFOzCCAyOgAwIBAgITGAAAA4HCMidgplmlrQAAAAADgTANBgkqhkiG9w0BAQ0FADA3MRcwFQYDVQQDDA5EZW1vIE1TIFN1YiBDQTEcMBoGA1UECgwTM0tleSBDb21wYW55IHMuci5vLjAeFw0yNDAzMTIxMTI3MDBaFw0yNjAzMTIxMTI3MDBaMBgxFjAUBgNVBAMTDXRmdC4za2V5LnRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDgoHj1WO_sXoqfcr7pm5KUjsAnmdjgzBbFwHCAvlDwsF8Z6B6i77AB-xLIUdcVLPu417mApEbH9Nu9jvcO_b2QEh_tDAczbug6SVMwgl7va3H5qGwg_0qepRHNWAMt1TWgY-rFZbUn1WLSO97armFjVPKK-AeuBVz4EQIqS1vxLLg0MxajR19euvkLBjbjYtjp7pwgHT2jMsccJ06bGN3Ik7wTZMnObfwxhhmwApEjyeDevVywopULc9zarvOSgFTnejlfOmwUBnnHlp8Xpq7P_izt1AhJkij9eElzSXnZUHAFQoQh3fQ6yelXFBxDOEAao8o3FR-R6Ss3kZ3mxkbjAgMBAAGjggFdMIIBWTAnBgNVHREEIDAegg10ZnQuM2tleS50ZXN0gg13d3cuM2tleS50ZXN0MB0GA1UdDgQWBBSA81KtARou26mp921nppmTGjC6BTAfBgNVHSMEGDAWgBSSwrzfVcXBk4VJB_esyR0LaAEHUTBNBgNVHR8ERjBEMEKgQKA-hjxodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2NybHMvZGVtby9EZW1vJTIwTVMlMjBTdWIlMjBDQS5jcmwwVwYIKwYBBQUHAQEESzBJMEcGCCsGAQUFBzABhjtodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2Nhcy9kZW1vL0RlbW8lMjBNUyUyMFN1YiUyMENBLmNydDAhBgkrBgEEAYI3FAIEFB4SAFcAZQBiAFMAZQByAHYAZQByMA4GA1UdDwEB_wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATANBgkqhkiG9w0BAQ0FAAOCAgEAb_N3sf9Kda5t_jsL_VQYW0OPiHD0V1QcwqiyplvclD7NahnV7QiUwS7V-QmHHD1V2_xkYNhlgkinu1SWbpJ8gAcLDbADfnMkaOZNr6dvKiDGw0Xppmfbha1Bbb3JA_DOHFrXBm3795mQDgaRuvPke0qyyL1DP9xAdxubQaYQDZA9WAYNztgVe3V4zngwzI6P6BiDQ7CgZLNv_e8e5ME4_MCeO0cUFxt7mzKIhH54wL4yY8DJ3LHVWXsMPntRMdvYWjYf-1Ivb5x2WvuU_SPcnCSyEj0qdcLlm9BWxbfM-5h4gXWvsCjG2anGLtsl5Ut3Sz1vvoM49N981pZEZDlNFlsBgYCF-MDKZwBOiX8uTgQkv5bqA7_tPvIgQI_JTbSYeqRtb4J6SH1_uRrhyU7w88PlSmZwkf5S5ZxX9eqjSEFENB7ARh4KaiHyYqTfYxAP6-EFs9dxBTQ5eQu2jFXy4xJG4g-r1KZujv6wgPoDZsbbqTfBg27_sQsTyzZqI1vL5UrCqxDSo-Pw9JPITYi8AdOffT0hkgQ7RmLHb6HYV7JqABmhZ3G9QQfuk2W7_o6l6jnpZM7pHEkZ30s54cIHgYG3JifXd2m6uxU6iX48mJy_VUZcVikxSbCg5eLlvq_HWnxk2DE_9PWjA_YxZs2Jtqpi2FtLCli2cykGGumhhJ0\",\"reason\":8}"));
        jwsObjectJSON.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(BASE_URI + RA_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        Assertions.assertThrows(NullPointerException.class,
                () -> acmeService.revokeCertificate(RA_PROFILE_NAME, acmeJwsRequestJSON, requestUri, true));

    }

    @Test
    public void testGetOrderList() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(BASE_URI + RA_PROFILE_NAME + "/orders/" + ACME_ACCOUNT_ID_VALID);

        ResponseEntity<List<Order>> orders = acmeService.listOrders(RA_PROFILE_NAME, ACME_ACCOUNT_ID_VALID, requestUri, true);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, orders.getStatusCode());
        Assertions.assertNotNull(orders);
    }

    @Test
    public void testGetOrderListFail() {
        URI requestUri = URI.create(BASE_URI + RA_PROFILE_NAME + "/orders/" + ACME_ACCOUNT_ID_INVALID);

        Assertions.assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.listOrders(RA_PROFILE_NAME, ACME_ACCOUNT_ID_INVALID, requestUri, true));
    }

    @Test
    public void testGetOrder() throws AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(BASE_URI + RA_PROFILE_NAME + "/order/" + ORDER_ID_VALID);

        ResponseEntity<Order> orders = acmeService.getOrder(RA_PROFILE_NAME, ORDER_ID_VALID, requestUri, true);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, orders.getStatusCode());
        Assertions.assertNotNull(orders);
        // order status is VALID
        Assertions.assertEquals(OrderStatus.VALID, Objects.requireNonNull(orders.getBody()).getStatus());
    }

    @Test
    public void testKeyRollover() throws JOSEException, AcmeProblemDocumentException, NotFoundException {
        URI requestUri = URI.create(BASE_URI + RA_PROFILE_NAME + "/key-change");

        String account = BASE_URI + RA_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID;
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
                        .keyID(BASE_URI + RA_PROFILE_NAME + "/acct/" + ACME_ACCOUNT_ID_VALID)
                        .customParam(NONCE_HEADER_CUSTOM_PARAM, acmeValidNonce.getNonce())
                        .customParam(URL_HEADER_CUSTOM_PARAM, requestUri.toString())
                        .build(),
                rsa2048Signer
        );

        String acmeJwsRequestJSON = jwsObjectJSON.serializeFlattened();

        ResponseEntity<?> response = acmeService.keyRollover(RA_PROFILE_NAME, acmeJwsRequestJSON, requestUri, true);

        // status code is 200
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        // contains Link header
        Assertions.assertNotNull(response.getHeaders().get("Link"));
    }
}

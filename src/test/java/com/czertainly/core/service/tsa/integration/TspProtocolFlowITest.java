package com.czertainly.core.service.tsa.integration;

import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowRequestDto;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.common.enums.cryptography.RsaSignatureScheme;
import com.czertainly.api.model.client.attribute.RequestAttributeV2;
import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.oid.SystemOid;
import com.czertainly.api.model.client.connector.v2.ConnectorVersion;
import com.czertainly.core.attribute.RsaSignatureAttributes;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.api.tsp.TspControllerImpl;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.TspProfileService;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * End-to-end integration test for the RFC 3161 Timestamp Protocol implementation.
 *
 * <p>Exercises the full timestamp-token production path via the service layer:
 * <ol>
 *   <li>Infrastructure setup – connector, token instance, token profile, cryptographic key,
 *       TSA certificate, signing profile, TSP profile</li>
 *   <li>TSP request construction (BouncyCastle {@link TimeStampRequestGenerator})</li>
 *   <li>{@link TspControllerImpl#timestamp} invocation</li>
 *   <li>RFC 3161 {@link TimeStampResponse} parsing and PKI status assertion</li>
 * </ol>
 *
 * <p>The external Cryptography Provider Connector is stubbed with WireMock.
 * The sign endpoint dynamically computes a real SHA256withRSA signature over the
 * incoming DTBS using the same key pair as the TSA certificate, so that the assembled
 * timestamp token carries a cryptographically valid CMS signature.
 * Signature verification is enabled via {@code validateTokenSignature = true} on the
 * signing profile workflow, exercising the full end-to-end path including
 * {@link com.czertainly.core.service.tsa.TimestampEngine} token validation.
 */
public class TspProtocolFlowITest extends BaseSpringBootTest {

    // ── Spring beans ──────────────────────────────────────────────────────────

    @Autowired
    private TspControllerImpl tspController;
    @Autowired
    private SigningProfileService signingProfileService;
    @Autowired
    private TspProfileService tspProfileService;

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;
    @Autowired
    private TokenProfileRepository tokenProfileRepository;
    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;
    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;

    // ── Test constants ────────────────────────────────────────────────────────

    private static final String TSP_PROFILE_NAME = "testTspProfile";
    private static final String SIGNING_PROFILE_NAME = "testSigningProfile";

    // ── Per-test state ────────────────────────────────────────────────────────

    private WireMockServer wireMockServer;
    /** In-memory RSA key pair used to build the TSA certificate. */
    private KeyPair tsaKeyPair;
    /** Self-signed X.509 TSA certificate with critical id-kp-timeStamping EKU. */
    private X509Certificate tsaCert;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    public void setUp() throws Exception {
        ensureBouncyCastleProvider();

        // Key pair must be generated first so the WireMock transformer can sign with it.
        tsaKeyPair = generateRsaKeyPair();

        wireMockServer = new WireMockServer(
                WireMockConfiguration.options()
                        .port(0)
                        .extensions(new RealRsaSignerTransformer(tsaKeyPair.getPrivate())));
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        stubSignEndpoint();
        buildInfrastructure();
    }

    @AfterEach
    public void tearDown() {
        wireMockServer.stop();
    }

    // ── Test ──────────────────────────────────────────────────────────────────

    /**
     * Happy-path end-to-end flow: build TSP request → call controller → assert GRANTED.
     */
    @Test
    public void tspFullTimestampFlow() throws Exception {

        // Build a SHA-256 TSP request over some arbitrary data imprint
        byte[] data = "Hello, Timestamp!".getBytes();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(data);

        TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
        gen.setCertReq(true);
        TimeStampRequest tsRequest = gen.generate(TSPAlgorithms.SHA256, hash, BigInteger.valueOf(System.currentTimeMillis()));
        byte[] requestBytes = tsRequest.getEncoded();

        // Invoke TspController
        ResponseEntity<byte[]> response = tspController.timestamp(TSP_PROFILE_NAME, requestBytes);

        // HTTP 200
        Assertions.assertEquals(200, response.getStatusCode().value());
        byte[] responseBytes = response.getBody();
        Assertions.assertNotNull(responseBytes);
        Assertions.assertTrue(responseBytes.length > 0, "Response body must not be empty");

        // Parse and validate the TimeStampResponse
        TimeStampResponse tsResponse = new TimeStampResponse(responseBytes);
        Assertions.assertEquals(
                org.bouncycastle.asn1.cmp.PKIStatus.GRANTED,
                tsResponse.getStatus(),
                "Expected PKIStatus GRANTED (0) but got: " + tsResponse.getStatus()
                        + " — " + tsResponse.getStatusString()
        );

        // Token must be present and carry the correct message imprint algorithm
        Assertions.assertNotNull(tsResponse.getTimeStampToken(), "TimeStampToken must be present");
        String imprintAlg = tsResponse.getTimeStampToken().getTimeStampInfo().getMessageImprintAlgOID().getId();
        Assertions.assertEquals(TSPAlgorithms.SHA256.getId(), imprintAlg,
                "Message imprint algorithm must be SHA-256");
    }

    // ── Infrastructure setup ──────────────────────────────────────────────────

    private void buildInfrastructure() throws Exception {
        // tsaKeyPair was already generated in setUp() so the WireMock transformer has the private key.
        tsaCert = buildTsaCertificate(tsaKeyPair);

        Connector connector = persistConnector();
        TokenInstanceReference tokenInstance = persistTokenInstance(connector);
        TokenProfile tokenProfile = persistTokenProfile(tokenInstance);
        CryptographicKey key = persistCryptographicKey(tokenInstance, tokenProfile, tsaKeyPair);
        Certificate certificate = persistTsaCertificate(key, tsaCert);

        createSigningProfile(certificate);
        createTspProfile();
    }

    /**
     * Stub: POST /v1/cryptographyProvider/tokens/{any}/keys/{any}/sign → 200 with real signature.
     *
     * <p>The {@link RealRsaSignerTransformer} extension intercepts the request, extracts the
     * base64-encoded DTBS from the JSON body, signs it with {@code SHA256withRSA} using the
     * test TSA private key, and returns the real signature so the assembled timestamp token
     * is cryptographically valid.
     */
    private void stubSignEndpoint() {
        wireMockServer.stubFor(
                post(urlPathMatching("/v1/cryptographyProvider/tokens/.+/keys/.+/sign"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withTransformers("real-rsa-signer"))
        );
    }

    private Connector persistConnector() {
        Connector connector = new Connector();
        connector.setName("tsp-crypto-connector");
        connector.setUrl("http://localhost:" + wireMockServer.port());
        connector.setVersion(ConnectorVersion.V1);
        connector.setStatus(ConnectorStatus.CONNECTED);
        return connectorRepository.save(connector);
    }

    private TokenInstanceReference persistTokenInstance(Connector connector) {
        TokenInstanceReference ref = new TokenInstanceReference();
        ref.setName("tsp-token-instance");
        ref.setTokenInstanceUuid(UUID.randomUUID().toString());
        ref.setConnector(connector);
        ref.setStatus(TokenInstanceStatus.CONNECTED);
        return tokenInstanceReferenceRepository.saveAndFlush(ref);
    }

    private TokenProfile persistTokenProfile(TokenInstanceReference tokenInstance) {
        TokenProfile profile = new TokenProfile();
        profile.setName("tsp-token-profile");
        profile.setTokenInstanceReference(tokenInstance);
        profile.setTokenInstanceName(tokenInstance.getName());
        profile.setEnabled(true);
        return tokenProfileRepository.saveAndFlush(profile);
    }

    private CryptographicKey persistCryptographicKey(TokenInstanceReference tokenInstance,
                                                      TokenProfile tokenProfile,
                                                      KeyPair keyPair) {
        CryptographicKey key = new CryptographicKey();
        key.setName("tsp-rsa-key");
        key.setTokenProfile(tokenProfile);
        key.setTokenInstanceReference(tokenInstance);
        key = cryptographicKeyRepository.saveAndFlush(key);

        // Private key item
        CryptographicKeyItem privateItem = new CryptographicKeyItem();
        privateItem.setKey(key);
        privateItem.setKeyUuid(key.getUuid());
        privateItem.setType(KeyType.PRIVATE_KEY);
        privateItem.setState(com.czertainly.api.model.core.cryptography.key.KeyState.ACTIVE);
        privateItem.setEnabled(true);
        privateItem.setKeyAlgorithm(KeyAlgorithm.RSA);
        privateItem.setLength(2048);
        privateItem.setUsage(List.of(com.czertainly.api.model.core.cryptography.key.KeyUsage.SIGN));
        privateItem = cryptographicKeyItemRepository.saveAndFlush(privateItem);
        privateItem.setKeyReferenceUuid(privateItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(privateItem);

        // Public key item — keyData carries the base64-encoded SubjectPublicKeyInfo (used by
        // resolveSignatureAlgorithmName for FALCON/MLDSA; for RSA it is unused but must be present)
        String pubKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        CryptographicKeyItem publicItem = new CryptographicKeyItem();
        publicItem.setKey(key);
        publicItem.setKeyUuid(key.getUuid());
        publicItem.setType(KeyType.PUBLIC_KEY);
        publicItem.setState(com.czertainly.api.model.core.cryptography.key.KeyState.ACTIVE);
        publicItem.setEnabled(true);
        publicItem.setKeyAlgorithm(KeyAlgorithm.RSA);
        publicItem.setLength(2048);
        publicItem.setUsage(List.of(com.czertainly.api.model.core.cryptography.key.KeyUsage.SIGN));
        publicItem.setKeyData(pubKeyBase64);
        publicItem = cryptographicKeyItemRepository.saveAndFlush(publicItem);
        publicItem.setKeyReferenceUuid(publicItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(publicItem);

        return cryptographicKeyRepository.findById(key.getUuid()).orElseThrow();
    }

    /**
     * Persists a Certificate entity with the TSA X.509 certificate content and all
     * conditions required by {@code isCertificateDigitalSigningAcceptable} for TIMESTAMPING:
     * <ul>
     *   <li>state = ISSUED, validationStatus = VALID</li>
     *   <li>key has a token profile</li>
     *   <li>extendedKeyUsage = [id-kp-timeStamping], critical = true</li>
     * </ul>
     */
    private Certificate persistTsaCertificate(CryptographicKey key, X509Certificate x509) throws Exception {
        // Persist certificate content (base64-encoded DER without PEM headers, matching normalizeCertificateContent)
        String derBase64 = Base64.getEncoder().encodeToString(x509.getEncoded());
        String fingerprint = CertificateUtil.getThumbprint(x509.getEncoded());

        CertificateContent content = new CertificateContent();
        content.setContent(derBase64);
        content.setFingerprint(fingerprint);
        content = certificateContentRepository.saveAndFlush(content);

        Certificate cert = new Certificate();
        cert.setKey(key);
        cert.setState(CertificateState.ISSUED);
        cert.setValidationStatus(CertificateValidationStatus.VALID);
        cert.setFingerprint(fingerprint);
        cert.setCertificateContent(content);
        // RFC 3161: exactly id-kp-timeStamping, critical
        cert.setExtendedKeyUsage(MetaDefinitions.serializeArrayString(List.of(SystemOid.TIME_STAMPING.getOid())));
        cert.setExtendedKeyUsageCritical(true);
        return certificateRepository.saveAndFlush(cert);
    }

    private void createSigningProfile(Certificate certificate) throws Exception {
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(certificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));

        TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
        workflow.setQualifiedTimestamp(false);
        workflow.setDefaultPolicyId("1.2.3.4.5");
        workflow.setValidateTokenSignature(true);

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(SIGNING_PROFILE_NAME);
        request.setDescription("TSP integration test signing profile");
        request.setSigningScheme(scheme);
        request.setWorkflow(workflow);

        signingProfileService.createSigningProfile(request);
    }

    private void createTspProfile() throws Exception {
        TspProfileRequestDto request = new TspProfileRequestDto();
        request.setName(TSP_PROFILE_NAME);
        request.setDescription("TSP integration test profile");

        // Resolve the signing profile UUID by name via service
        com.czertainly.api.model.client.signing.profile.SigningProfileDto signingProfile =
                signingProfileService.getSigningProfile(SIGNING_PROFILE_NAME);
        request.setDefaultSigningProfileUuid(UUID.fromString(signingProfile.getUuid()));

        tspProfileService.createTspProfile(request);
    }

    // ── Crypto helpers ────────────────────────────────────────────────────────

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    /**
     * Builds a self-signed X.509 certificate with critical id-kp-timeStamping EKU.
     * This satisfies both {@link com.czertainly.core.service.tsa.certificatevalidation.TimestampingEkuValidator}
     * (run against the decoded X509Certificate) and {@code isCertificateDigitalSigningAcceptable}
     * (run against the Certificate entity's metadata fields).
     */
    private static X509Certificate buildTsaCertificate(KeyPair keyPair) throws Exception {
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        X500Principal subject = new X500Principal("CN=Test TSA, O=CZERTAINLY, C=CZ");

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(System.currentTimeMillis()),
                notBefore, notAfter, subject, keyPair.getPublic());

        // Critical id-kp-timeStamping EKU (RFC 3161 requirement)
        certBuilder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certBuilder.build(signer));
    }

    private static RequestAttributeV2 buildRsaSchemeAttribute(RsaSignatureScheme scheme) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME);
        attr.setContentType(AttributeContentType.STRING);
        attr.setContent(List.of(new StringAttributeContentV2(scheme.getLabel(), scheme.getCode())));
        return attr;
    }

    private static RequestAttributeV2 buildDigestAttribute(DigestAlgorithm algorithm) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST);
        attr.setContentType(AttributeContentType.STRING);
        attr.setContent(List.of(new StringAttributeContentV2(algorithm.getLabel(), algorithm.getCode())));
        return attr;
    }

    // ── WireMock transformer ──────────────────────────────────────────────────

    /**
     * WireMock extension that computes a real {@code SHA256withRSA} signature over the incoming
     * DTBS so the assembled timestamp token is cryptographically valid.
     *
     * <p>It parses the connector sign-request JSON ({@code data[0].data} = base64 DTBS),
     * signs the decoded bytes with the test TSA private key, and returns the signature
     * in the connector sign-response JSON ({@code signatures[0].data} = base64 signature).
     */
    private static class RealRsaSignerTransformer implements ResponseDefinitionTransformerV2 {

        private final PrivateKey privateKey;

        RealRsaSignerTransformer(PrivateKey privateKey) {
            this.privateKey = privateKey;
        }

        @Override
        public ResponseDefinition transform(ServeEvent serveEvent) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode body = objectMapper.readTree(serveEvent.getRequest().getBodyAsString());
                byte[] dtbs = Base64.getDecoder().decode(body.at("/data/0/data").asText());

                java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
                sig.initSign(privateKey);
                sig.update(dtbs);
                byte[] signature = sig.sign();

                String responseBody = objectMapper.writeValueAsString(
                        new SignDataConnectorResponse(List.of(new SignatureEntry(signature))));

                return ResponseDefinitionBuilder.responseDefinition()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute SHA256withRSA signature in WireMock transformer", e);
            }
        }

        @Override
        public String getName() {
            return "real-rsa-signer";
        }
    }

    // ── WireMock response POJOs ───────────────────────────────────────────────

    /** Matches the connector-side {@code SignDataResponseDto} JSON structure. */
    record SignDataConnectorResponse(List<SignatureEntry> signatures) {}

    /** Matches the connector-side {@code SignatureResponseData} JSON structure. */
    record SignatureEntry(byte[] data) {}
}

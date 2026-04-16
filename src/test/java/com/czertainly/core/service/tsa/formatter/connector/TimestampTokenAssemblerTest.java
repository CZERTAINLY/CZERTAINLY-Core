package com.czertainly.core.service.tsa.formatter.connector;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.core.service.tsa.CertificateChain;
import com.czertainly.core.service.tsa.messages.TspRequestBuilder;
import com.czertainly.core.util.CertificateTestUtil;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.cert.X509CertificateHolder;

import java.util.Collection;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tsp.TimeStampToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class TimestampTokenAssemblerTest {

    private static final SignatureAlgorithm SIGNING_ALG = SignatureAlgorithm.SHA256withRSA;
    private static final String POLICY_OID = "1.2.3.4.5";
    /** Structurally valid 2048-bit RSA signature length — not cryptographically meaningful. */
    private static final byte[] DUMMY_SIGNATURE = new byte[256];

    private static KeyPair rsaKeyPair;
    private static X509Certificate tsaCertificate;

    @BeforeAll
    static void setUpCrypto() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        rsaKeyPair = gen.generateKeyPair();
        tsaCertificate = CertificateTestUtil.createTimestampingCertificate(rsaKeyPair);
    }

    // ── formatDtbs ────────────────────────────────────────────────────────────

    @Test
    void formatDtbs_returnsNonEmptyBytes() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var request = TspRequestBuilder.valid();

        // when
        byte[] dtbs = TimestampTokenAssembler.formatDtbs(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, false, request);

        // then
        assertNotNull(dtbs);
        assertTrue(dtbs.length > 0);
    }

    // ── formatSigningResponse ─────────────────────────────────────────────────

    @Test
    void twoPhaseRoundtrip_producesSignatureVerifiableToken() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var request = TspRequestBuilder.valid();
        var genTime = new Date();

        // Phase 1: capture signed attributes (DTBS)
        byte[] dtbs = TimestampTokenAssembler.formatDtbs(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, genTime, null,
                false, false, false, false, request);

        // Sign DTBS with the TSA private key
        var sig = Signature.getInstance("SHA256withRSA", BouncyCastleProvider.PROVIDER_NAME);
        sig.initSign(rsaKeyPair.getPrivate());
        sig.update(dtbs);
        byte[] signature = sig.sign();

        // Phase 2: assemble the final token with the injected signature
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, genTime, null,
                false, false, false, false, request, signature);

        // then: the CMS signature verifies against the TSA certificate
        assertDoesNotThrow(() -> token.validate(
                new JcaSimpleSignerInfoVerifierBuilder()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(tsaCertificate)));
    }

    @Test
    void formatSigningResponse_tokenContainsExpectedSerialNumber() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var serialNumber = BigInteger.valueOf(42L);

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                serialNumber, new Date(), null,
                false, false, false, false, TspRequestBuilder.valid(), DUMMY_SIGNATURE);

        // then
        assertEquals(serialNumber, token.getTimeStampInfo().getSerialNumber());
    }

    @Test
    void formatSigningResponse_tokenContainsNonce_whenRequestIncludesNonce() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var nonce = BigInteger.valueOf(987654321L);
        var request = TspRequestBuilder.aTspRequest().nonce(nonce).build();

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, false, request, DUMMY_SIGNATURE);

        // then
        assertEquals(nonce, token.getTimeStampInfo().getNonce());
    }

    @Test
    void formatSigningResponse_tokenContainsMessageImprintAlgorithm_fromRequest() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var request = TspRequestBuilder.aTspRequest().hashAlgorithm(DigestAlgorithm.SHA_384).build();

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, false, request, DUMMY_SIGNATURE);

        // then
        assertEquals(DigestAlgorithm.SHA_384.getOid(),
                token.getTimeStampInfo().getMessageImprintAlgOID().getId());
    }

    @Test
    void formatSigningResponse_tokenContainsMessageImprintHash_fromRequest() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var expectedHash = new byte[32];
        expectedHash[0] = 0x42; // distinct from the all-zero default, so the assertion is meaningful
        var request = TspRequestBuilder.aTspRequest().hashedMessage(expectedHash).build();

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, false, request, DUMMY_SIGNATURE);

        // then
        assertArrayEquals(expectedHash, token.getTimeStampInfo().getMessageImprintDigest());
    }

    @Test
    void formatSigningResponse_tokenContainsRequestExtensions() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var customOid = new ASN1ObjectIdentifier("1.2.3.99");
        var extGen = new ExtensionsGenerator();
        extGen.addExtension(customOid, false, new ASN1Integer(42));
        var request = TspRequestBuilder.aTspRequest().requestExtensions(extGen.generate()).build();

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, false, request, DUMMY_SIGNATURE);

        // then
        var extensions = token.getTimeStampInfo().toASN1Structure().getExtensions();
        assertNotNull(extensions);
        assertNotNull(extensions.getExtension(customOid),
                "Custom request extension must be present in TSTInfo");
    }

    @Test
    void formatSigningResponse_throwsTspException_whenRequestPolicyIsInvalidOid() {
        // given - "9.9.9.9.9" is not a valid OID (first arc must be 0, 1, or 2)
        var chain = CertificateChain.of(tsaCertificate);
        var request = TspRequestBuilder.aTspRequest().policy("9.9.9.9.9").build();

        // when / then
        var exception = assertThrows(TspException.class, () ->
                TimestampTokenAssembler.formatSigningResponse(
                        SIGNING_ALG, chain, POLICY_OID,
                        BigInteger.ONE, new Date(), null,
                        false, false, false, false,  request, DUMMY_SIGNATURE));
        assertEquals(TspFailureInfo.BAD_REQUEST, exception.getFailureInfo());
    }

    @Test
    void formatSigningResponse_includesQcStatementsExtension_forQualifiedTimestamp() throws Exception {
        // given - qualifiedTimestamp=true triggers the ETSI EN 319 422 Annex B esi4-qtstStatement1 extension
        var chain = CertificateChain.of(tsaCertificate);

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, true, TspRequestBuilder.valid(), DUMMY_SIGNATURE);

        // then: qCStatements extension must be present in the TSTInfo
        var extensions = token.getTimeStampInfo().toASN1Structure().getExtensions();
        assertNotNull(extensions, "TSTInfo extensions must be present for qualified timestamp");
        assertNotNull(extensions.getExtension(Extension.qCStatements),
                "qCStatements extension (OID 2.5.29.56) must be present");
    }

    @Test
    void formatSigningResponse_omitsQcStatementsExtension_forNonQualifiedTimestamp() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, false, TspRequestBuilder.valid(), DUMMY_SIGNATURE);

        // then
        var extensions = token.getTimeStampInfo().toASN1Structure().getExtensions();
        assertTrue(extensions == null || extensions.getExtension(Extension.qCStatements) == null,
                "qCStatements extension must not be present for non-qualified timestamp");
    }

    @Test
    void formatSigningResponse_embedsCertificate_whenIncludeSignerCertificateIsTrue() throws Exception {
        // given
        var chain = CertificateChain.of(tsaCertificate);
        var request = TspRequestBuilder.aTspRequest().includeSignerCertificate(true).build();

        // when
        TimeStampToken token = TimestampTokenAssembler.formatSigningResponse(
                SIGNING_ALG, chain, POLICY_OID,
                BigInteger.ONE, new Date(), null,
                false, false, false, false, request, DUMMY_SIGNATURE);

        // then: the exact TSA certificate is present in the CMS certificate store
        Collection<X509CertificateHolder> embeddedCerts = token.getCertificates().getMatches(null);
        assertEquals(1, embeddedCerts.size(), "one certificate must be embedded in the token");
        assertEquals(tsaCertificate.getSerialNumber(), embeddedCerts.iterator().next().getSerialNumber(),
                "the embedded certificate must be the TSA signing certificate");
    }

}

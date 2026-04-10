package com.czertainly.core.service.tsa.formatter.connector;

import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ExtendedContentSigner;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * BouncyCastle ContentSigner that captures the bytes written to it
 * (the DER-encoded SignedAttributes) and returns an empty signature.
 *
 * <p>Used in DTBS formatting to extract the data-to-be-signed
 * without producing a real signature.
 */
class CapturingContentSigner implements ExtendedContentSigner {

    private final SignatureAlgorithm signatureAlgorithm;
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private byte[] capturedBytes;

    CapturingContentSigner(SignatureAlgorithm signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return signatureAlgorithm.getAlgorithmIdentifier();
    }

    @Override
    public AlgorithmIdentifier getDigestAlgorithmIdentifier() {
        return signatureAlgorithm.getDigestAlgorithmIdentifier();
    }

    @Override
    public OutputStream getOutputStream() {
        return stream;
    }

    @Override
    public byte[] getSignature() {
        capturedBytes = stream.toByteArray();
        return new byte[0];
    }

    byte[] getCapturedBytes() {
        if (capturedBytes == null) {
            throw new IllegalStateException(
                    "Phase 1 not completed: call TimeStampTokenGenerator.generate() first");
        }
        return capturedBytes.clone();
    }
}

package com.czertainly.core.service.tsa.formatter.connector;

import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ExtendedContentSigner;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * BouncyCastle ContentSigner that returns a pre-computed signature.
 *
 * <p>Used in token assembly where the signature has already been
 * produced by an external signing service. The bytes written by BouncyCastle
 * are consumed but not used — the injected signature is returned as-is.
 */
class InjectingContentSigner implements ExtendedContentSigner {

    private final SignatureAlgorithm signatureAlgorithm;
    private final byte[] signature;
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

    InjectingContentSigner(SignatureAlgorithm signatureAlgorithm, byte[] signature) {
        this.signatureAlgorithm = signatureAlgorithm;
        this.signature = Objects.requireNonNull(signature, "signature must not be null").clone();
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
        return signature.clone();
    }
}

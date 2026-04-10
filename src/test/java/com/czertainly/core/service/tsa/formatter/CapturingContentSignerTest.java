package com.czertainly.core.service.tsa.formatter;

import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.core.service.tsa.formatter.connector.CapturingContentSigner;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CapturingContentSignerTest {

    private static final SignatureAlgorithm ALG = SignatureAlgorithm.SHA256withRSA;

    @Test
    void getAlgorithmIdentifier_returnsConstructorValue() {
        var signer = new CapturingContentSigner(ALG);
        assertEquals(ALG.getAlgorithmIdentifier(), signer.getAlgorithmIdentifier());
    }

    @Test
    void getSignature_returnsEmptyArray() {
        var signer = new CapturingContentSigner(ALG);
        assertArrayEquals(new byte[0], signer.getSignature());
    }

    @Test
    void capture_recordsBytesWrittenToStream() throws IOException {
        var signer = new CapturingContentSigner(ALG);
        signer.getOutputStream().write(new byte[]{1, 2, 3});
        signer.getSignature();
        assertArrayEquals(new byte[]{1, 2, 3}, signer.getCapturedBytes());
    }

    @Test
    void getCapturedBytes_throwsBeforeGetSignatureCalled() {
        var signer = new CapturingContentSigner(ALG);
        assertThrows(IllegalStateException.class, signer::getCapturedBytes);
    }

    @Test
    void getCapturedBytes_returnsEmptyArrayAfterZeroByteCapture() {
        var signer = new CapturingContentSigner(ALG);
        signer.getSignature();
        assertArrayEquals(new byte[0], signer.getCapturedBytes());
    }
}

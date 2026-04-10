package com.czertainly.core.service.tsa.formatter;

import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.core.service.tsa.formatter.connector.InjectingContentSigner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InjectingContentSignerTest {

    private static final SignatureAlgorithm ALG = SignatureAlgorithm.SHA256withRSA;

    @Test
    void getAlgorithmIdentifier_returnsConstructorValue() {
        var signer = new InjectingContentSigner(ALG, new byte[]{1});
        assertEquals(ALG.getAlgorithmIdentifier(), signer.getAlgorithmIdentifier());
    }

    @Test
    void getSignature_returnsInjectedSignature() {
        byte[] sig = {0x50, 0x51, 0x52};
        var signer = new InjectingContentSigner(ALG, sig);
        assertArrayEquals(sig, signer.getSignature());
    }

    @Test
    void getSignature_returnsDefensiveCopy() {
        byte[] sig = {1, 2, 3};
        var signer = new InjectingContentSigner(ALG, sig);
        byte[] returned = signer.getSignature();
        returned[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, signer.getSignature());
    }

    @Test
    void constructor_throwsOnNullSignature() {
        assertThrows(NullPointerException.class,
                () -> new InjectingContentSigner(ALG, null));
    }
}

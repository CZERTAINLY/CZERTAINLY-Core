package com.otilm.core.service.cmp.message.validator.impl;

import com.otilm.api.interfaces.core.cmp.error.CmpProcessingException;
import com.otilm.api.model.core.cmp.ProtectionMethod;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.CMPObjectIdentifiers;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the protection-type consistency check in {@link ProtectionValidator}.
 *
 * <p>Regression coverage for issue #1885: a message whose actual protection type contradicts the profile's
 * configured {@code Requested Protection Method} must be rejected with a proper CMP {@code badMessageCheck}
 * rejection (RFC 4210 §5.2.7) rather than being allowed through to a strategy that assumes a different type.</p>
 */
class ProtectionValidatorTest {

    private static final ASN1OctetString TID = new DEROctetString(new byte[]{1, 2, 3, 4});
    private static final AlgorithmIdentifier SIGNATURE_ALG =
            new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.2.840.10045.4.3.3")); // ecdsa-with-SHA384
    private static final AlgorithmIdentifier PBM_ALG =
            new AlgorithmIdentifier(CMPObjectIdentifiers.passwordBasedMac);
    private static final AlgorithmIdentifier PBMAC1_ALG =
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBMAC1);

    @Test
    void rejectsSignatureMessage_whenProfileRequiresSharedSecret() {
        assertThatThrownBy(() -> ProtectionValidator.assertMessageProtectionMatchesProfile(
                TID, SIGNATURE_ALG, ProtectionMethod.SHARED_SECRET))
                .isInstanceOfSatisfying(CmpProcessingException.class,
                        e -> assertThat(e.getFailureInfo()).isEqualTo(PKIFailureInfo.badMessageCheck));
    }

    @Test
    void rejectsPbmMessage_whenProfileRequiresSignature() {
        assertThatThrownBy(() -> ProtectionValidator.assertMessageProtectionMatchesProfile(
                TID, PBM_ALG, ProtectionMethod.SIGNATURE))
                .isInstanceOfSatisfying(CmpProcessingException.class,
                        e -> assertThat(e.getFailureInfo()).isEqualTo(PKIFailureInfo.badMessageCheck));
    }

    @Test
    void acceptsPbmMessage_whenProfileRequiresSharedSecret() {
        assertThatCode(() -> ProtectionValidator.assertMessageProtectionMatchesProfile(
                TID, PBM_ALG, ProtectionMethod.SHARED_SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsPbmac1Message_whenProfileRequiresSharedSecret() {
        assertThatCode(() -> ProtectionValidator.assertMessageProtectionMatchesProfile(
                TID, PBMAC1_ALG, ProtectionMethod.SHARED_SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsSignatureMessage_whenProfileRequiresSignature() {
        assertThatCode(() -> ProtectionValidator.assertMessageProtectionMatchesProfile(
                TID, SIGNATURE_ALG, ProtectionMethod.SIGNATURE))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAnyMessage_whenProfileDoesNotConstrainRequestMethod() {
        assertThatCode(() -> ProtectionValidator.assertMessageProtectionMatchesProfile(
                TID, SIGNATURE_ALG, null))
                .doesNotThrowAnyException();
    }
}

package com.otilm.core.signing.contentsigning;

import com.otilm.api.model.common.enums.cryptography.DigestAlgorithm;
import com.otilm.api.model.connector.signatures.contentsigning.common.ComputeDtbsResponseDto;
import com.otilm.core.signing.engine.error.SigningEngineException;
import com.otilm.core.signing.engine.error.SigningEngineFailure;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DtbsBindingVerifierTest {

    private static final byte[] AUTHORIZED_VALUE = digestOfLength(32, (byte) 0x11);

    private static final DocumentDigest AUTHORIZED = new DocumentDigest(DigestAlgorithm.SHA_256, AUTHORIZED_VALUE);

    @Test
    void acceptsAnEchoOfTheAuthorizedDocument() {
        // given a connector echoing an equal array rather than the very same one
        DocumentDigest echoed = new DocumentDigest(DigestAlgorithm.SHA_256, AUTHORIZED_VALUE.clone());

        // when / then
        assertThatCode(() -> DtbsBindingVerifier.verify(AUTHORIZED, echoed)).doesNotThrowAnyException();
    }

    @Test
    void refusesAnEchoOfADifferentDocument() {
        // given
        DocumentDigest echoed = new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x22));

        // when / then
        SigningEngineException thrown = catchVerify(AUTHORIZED, echoed);
        assertThat(thrown.failure()).isEqualTo(SigningEngineFailure.BINDING_VIOLATION);
        assertThat(thrown.step()).isEqualTo("computeDtbs");
        assertThat(thrown.operatorMessage()).contains("2222", "1111");
    }

    @Test
    void refusesAnEchoDifferingInASingleBit() {
        // given
        byte[] almost = AUTHORIZED_VALUE.clone();
        almost[31] ^= 0x01;

        // when / then
        assertThat(catchVerify(AUTHORIZED, new DocumentDigest(DigestAlgorithm.SHA_256, almost)).failure())
                .isEqualTo(SigningEngineFailure.BINDING_VIOLATION);
    }

    @Test
    void refusesAMissingEcho() {
        // when / then
        SigningEngineException thrown = catchVerify(AUTHORIZED, (DocumentDigest) null);
        assertThat(thrown.failure()).isEqualTo(SigningEngineFailure.CONNECTOR_FAULT);
        assertThat(thrown.operatorMessage()).contains("echoed no documentDigest");
    }

    /** Digests of different algorithms are incomparable, so the platform refuses instead of re-deriving one. */
    @Test
    void refusesAnEchoUnderADifferentAlgorithm() {
        // given
        DocumentDigest echoed = new DocumentDigest(DigestAlgorithm.SHA_512, digestOfLength(64, (byte) 0x11));

        // when / then
        assertThat(catchVerify(AUTHORIZED, echoed).operatorMessage()).contains("SHA-512", "SHA-256");
    }

    @Test
    void refusesAnEchoTooShortForItsAlgorithm() {
        // given
        DocumentDigest echoed = new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(16, (byte) 0x11));

        // when / then
        assertThat(catchVerify(AUTHORIZED, echoed).operatorMessage()).contains("16-byte", "never produces");
    }

    /** A broken connector must not be reported to the client as an unauthorized document. */
    @Test
    void tellsABrokenEchoApartFromAMismatchOnTheWire() {
        // given
        DocumentDigest different = new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x22));
        DocumentDigest unusable = new DocumentDigest(DigestAlgorithm.SHA_512, digestOfLength(64, (byte) 0x11));

        // when / then
        assertThat(catchVerify(AUTHORIZED, different).clientMessage())
                .isNotEqualTo(catchVerify(AUTHORIZED, unusable).clientMessage());
    }

    /** Records and alerting key off the failure value, so the security event must not collapse into a fault. */
    @Test
    void classifiesAMismatchApartFromABrokenEcho() {
        // given
        DocumentDigest different = new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x22));
        DocumentDigest unusable = new DocumentDigest(DigestAlgorithm.SHA_512, digestOfLength(64, (byte) 0x11));

        // when / then
        assertThat(catchVerify(AUTHORIZED, different).failure()).isEqualTo(SigningEngineFailure.BINDING_VIOLATION);
        assertThat(catchVerify(AUTHORIZED, unusable).failure()).isEqualTo(SigningEngineFailure.CONNECTOR_FAULT);
    }

    @Test
    void keepsDigestsOutOfTheClientMessage() {
        // given
        DocumentDigest echoed = new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x22));

        // when / then
        assertThat(catchVerify(AUTHORIZED, echoed).clientMessage()).doesNotContain("1111", "2222");
    }

    @Test
    void readsTheEchoOffAComputeDtbsResponse() {
        // when / then
        assertThatCode(
                () -> DtbsBindingVerifier.verify(AUTHORIZED, response(DigestAlgorithm.SHA_256, AUTHORIZED_VALUE)))
                .doesNotThrowAnyException();
        assertThat(
                catchVerify(AUTHORIZED, response(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x22))).failure())
                .isEqualTo(SigningEngineFailure.BINDING_VIOLATION);
    }

    @Test
    void treatsAResponseMissingEitherHalfOfTheEchoAsNoEcho() {
        // given
        ComputeDtbsResponseDto noDigest = response(DigestAlgorithm.SHA_256, null);
        ComputeDtbsResponseDto noAlgorithm = response(null, AUTHORIZED_VALUE);

        // when / then
        assertThat(catchVerify(AUTHORIZED, noDigest).operatorMessage()).contains("echoed no documentDigest");
        assertThat(catchVerify(AUTHORIZED, noAlgorithm).operatorMessage()).contains("echoed no documentDigest");
    }

    @Test
    void verifiesEveryDocumentOfAMultiDocumentOperation() {
        // given
        DocumentDigest second = new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x33));

        // when / then
        assertThatCode(() -> DtbsBindingVerifier
                .verifyAll(List.of(AUTHORIZED, second),
                        List
                                .of(response(DigestAlgorithm.SHA_256, AUTHORIZED_VALUE),
                                        response(DigestAlgorithm.SHA_256, second.value()))))
                .doesNotThrowAnyException();
    }

    /** Each document answers for itself: a set of echoes matching as a set is not a binding. */
    @Test
    void refusesSwappedEchoesInAMultiDocumentOperation() {
        // given
        DocumentDigest second = new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x33));
        List<ComputeDtbsResponseDto> swapped = List
                .of(response(DigestAlgorithm.SHA_256, second.value()),
                        response(DigestAlgorithm.SHA_256, AUTHORIZED_VALUE));

        // when / then
        assertThatExceptionOfType(SigningEngineException.class)
                .isThrownBy(() -> DtbsBindingVerifier.verifyAll(List.of(AUTHORIZED, second), swapped));
    }

    @Test
    void refusesToPairListsOfDifferentLengths() {
        // when / then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DtbsBindingVerifier
                        .verifyAll(List.of(AUTHORIZED),
                                List
                                        .of(response(DigestAlgorithm.SHA_256, AUTHORIZED_VALUE),
                                                response(DigestAlgorithm.SHA_256, AUTHORIZED_VALUE))));
    }

    @Test
    void acceptsAnEmptyMultiDocumentOperation() {
        // when / then
        assertThatCode(() -> DtbsBindingVerifier.verifyAll(List.of(), List.of())).doesNotThrowAnyException();
    }

    /** Whoever computed the authorized digest cannot move it afterwards by reusing the buffer it came in. */
    @Test
    void holdsTheAuthorizedDigestAgainstMutationOfItsSourceArray() {
        // given
        byte[] source = digestOfLength(32, (byte) 0x11);
        DocumentDigest authorized = new DocumentDigest(DigestAlgorithm.SHA_256, source);

        // when
        Arrays.fill(source, (byte) 0x22);

        // then
        assertThatCode(() -> DtbsBindingVerifier
                .verify(authorized, new DocumentDigest(DigestAlgorithm.SHA_256, digestOfLength(32, (byte) 0x11))))
                .doesNotThrowAnyException();
        assertThat(catchVerify(authorized, new DocumentDigest(DigestAlgorithm.SHA_256, source)).failure())
                .isEqualTo(SigningEngineFailure.BINDING_VIOLATION);
    }

    private static SigningEngineException catchVerify(DocumentDigest authorized, DocumentDigest echoed) {
        return catchThrowableOfType(SigningEngineException.class, () -> DtbsBindingVerifier.verify(authorized, echoed));
    }

    private static SigningEngineException catchVerify(DocumentDigest authorized, ComputeDtbsResponseDto response) {
        return catchThrowableOfType(SigningEngineException.class,
                () -> DtbsBindingVerifier.verify(authorized, response));
    }

    private static ComputeDtbsResponseDto response(DigestAlgorithm algorithm, byte[] digest) {
        ComputeDtbsResponseDto response = new ComputeDtbsResponseDto();
        response.setDocumentDigestAlgorithm(algorithm);
        response.setDocumentDigest(digest);
        return response;
    }

    private static byte[] digestOfLength(int length, byte fill) {
        byte[] digest = new byte[length];
        Arrays.fill(digest, fill);
        return digest;
    }
}

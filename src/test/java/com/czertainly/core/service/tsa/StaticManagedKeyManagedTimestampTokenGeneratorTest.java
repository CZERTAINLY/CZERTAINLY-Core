package com.czertainly.core.service.tsa;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.core.service.tsa.formatter.SignatureFormatterClient;
import com.czertainly.core.service.tsa.signer.Signer;
import com.czertainly.core.service.tsa.signer.SignerFactory;
import org.bouncycastle.tsp.TimeStampToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;

import static com.czertainly.core.model.signing.SigningProfileModelBuilder.aSigningProfile;
import static com.czertainly.core.service.tsa.messages.TspRequestBuilder.aTspRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticManagedKeyManagedTimestampTokenGeneratorTest {

    @Mock SignerFactory signerFactory;
    @Mock SignatureFormatterClient formatter;
    @Mock Signer signer;

    @InjectMocks
    StaticManagedKeyManagedTimestampTokenGenerator generator;

    /**
     * A real, parseable DER-encoded TimeStampToken used in happy-path tests.
     * Generated once via BouncyCastle so the unit tests do not need a live connector.
     */
    private static byte[] validTokenBytes;

    @BeforeAll
    static void generateValidTokenBytes() throws Exception {
        validTokenBytes = TimestampTokenTestUtil.createTimestampToken().getEncoded();
    }

    private SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ?> aTimestampingProfile() {
        return aSigningProfile().build();
    }

    @BeforeEach
    void wireSigner() throws TspException {
        lenient().when(signerFactory.create(any())).thenReturn(signer);
        lenient().when(signer.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.SHA256withRSA);
    }

    @Test
    void generate_returnsTimestampToken_whenAllDependenciesSucceed() throws Exception {
        // given
        var request = aTspRequest().build();
        var profile = aTimestampingProfile();
        var chain = mock(CertificateChain.class);
        var serialNumber = BigInteger.ONE;
        var genTime = Instant.parse("2024-01-01T00:00:00Z");
        byte[] dtbs = {1, 2, 3};
        byte[] signature = {4, 5, 6};

        when(formatter.formatDtbs(eq(request), eq(profile), eq(serialNumber), eq(genTime), eq(chain), eq(SignatureAlgorithm.SHA256withRSA)))
                .thenReturn(dtbs);
        when(signer.sign(eq(dtbs))).thenReturn(signature);
        when(formatter.formatSigningResponse(eq(request), eq(profile), eq(serialNumber), eq(genTime), eq(chain), eq(dtbs), eq(signature), eq(SignatureAlgorithm.SHA256withRSA)))
                .thenReturn(validTokenBytes);

        // when
        TimeStampToken token = generator.generate(request, profile, chain, serialNumber, genTime);

        // then
        assertThat(token).isNotNull();
    }

    @Test
    void generate_usesSigningSchemeFromProfile_toCreateSigner() throws Exception {
        // given — the factory must receive the scheme from the profile, not a default
        var profile = aTimestampingProfile();
        when(formatter.formatDtbs(any(), any(), any(), any(), any(), any())).thenReturn(new byte[1]);
        when(signer.sign(any())).thenReturn(new byte[1]);
        when(formatter.formatSigningResponse(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(validTokenBytes);

        // when
        generator.generate(aTspRequest().build(), profile, mock(CertificateChain.class), BigInteger.ONE, Instant.now());

        // then
        verify(signerFactory).create(profile.signingScheme());
    }

    @Test
    void generate_passesAlgorithmFromSigner_toBothFormatterPhases() throws Exception {
        // given — the formatter must receive the signer's reported algorithm in both the DTBS and signing-response phases
        when(signer.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.SHA384withECDSA);
        when(formatter.formatDtbs(any(), any(), any(), any(), any(), any())).thenReturn(new byte[1]);
        when(signer.sign(any())).thenReturn(new byte[1]);
        when(formatter.formatSigningResponse(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(validTokenBytes);

        // when
        generator.generate(aTspRequest().build(), aTimestampingProfile(),
                mock(CertificateChain.class), BigInteger.ONE, Instant.now());

        // then
        verify(formatter).formatDtbs(any(), any(), any(), any(), any(), eq(SignatureAlgorithm.SHA384withECDSA));
        verify(formatter).formatSigningResponse(any(), any(), any(), any(), any(), any(), any(),
                eq(SignatureAlgorithm.SHA384withECDSA));
    }

    @Test
    void generate_passesDtbsBytesToSigner_andSignatureToFormatterSigningResponse() throws Exception {
        // given — the DTBS from phase 1 must be fed to the signer, and the resulting signature must reach phase 2
        byte[] dtbs = {10, 20, 30};
        byte[] signature = {40, 50, 60};
        when(formatter.formatDtbs(any(), any(), any(), any(), any(), any())).thenReturn(dtbs);
        when(signer.sign(dtbs)).thenReturn(signature);
        when(formatter.formatSigningResponse(any(), any(), any(), any(), any(), eq(dtbs), eq(signature), any()))
                .thenReturn(validTokenBytes);

        // when
        generator.generate(aTspRequest().build(), aTimestampingProfile(),
                mock(CertificateChain.class), BigInteger.ONE, Instant.now());

        // then
        verify(signer).sign(dtbs);
        verify(formatter).formatSigningResponse(any(), any(), any(), any(), any(), eq(dtbs), eq(signature), any());
    }

    @Test
    void generate_throwsTspExceptionWithSystemFailure_whenTokenBytesAreNotParseable() throws Exception {
        // given — the formatter.formatSigningResponse returns garbage bytes; BouncyCastle fails to parse them as a CMS SignedData
        when(formatter.formatDtbs(any(), any(), any(), any(), any(), any())).thenReturn(new byte[1]);
        when(signer.sign(any())).thenReturn(new byte[1]);
        when(formatter.formatSigningResponse(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new byte[]{0x00, 0x01, 0x02, 0x03});

        // when / then
        var exception = assertThrows(TspException.class,
                () -> generator.generate(aTspRequest().build(), aTimestampingProfile(),
                        mock(CertificateChain.class), BigInteger.ONE, Instant.now()));

        assertThat(exception.getFailureInfo()).isEqualTo(TspFailureInfo.SYSTEM_FAILURE);
        assertThat(exception.getCause()).isNotNull();
    }

    @Test
    void generate_propagatesTspException_fromSignerFactory() throws Exception {
        // given — the factory cannot find a compatible signer for the profile's signing scheme
        var cause = new TspException(TspFailureInfo.SYSTEM_FAILURE, "no signer found", "system misconfigured");
        when(signerFactory.create(any())).thenThrow(cause);

        // when / then
        var thrown = assertThrows(TspException.class,
                () -> generator.generate(aTspRequest().build(), aTimestampingProfile(),
                        mock(CertificateChain.class), BigInteger.ONE, Instant.now()));

        assertThat(thrown).isSameAs(cause);
    }

    @Test
    void generate_propagatesTspException_fromFormatterDtbs() throws Exception {
        // given — the formatter fails to build the DTBS (e.g. malformed certificate)
        var cause = new TspException(TspFailureInfo.BAD_REQUEST, "cannot build DTBS", "bad request");
        when(formatter.formatDtbs(any(), any(), any(), any(), any(), any())).thenThrow(cause);

        // when / then
        var thrown = assertThrows(TspException.class,
                () -> generator.generate(aTspRequest().build(), aTimestampingProfile(),
                        mock(CertificateChain.class), BigInteger.ONE, Instant.now()));

        assertThat(thrown).isSameAs(cause);
    }

    @Test
    void generate_propagatesTspException_fromSigner() throws Exception {
        // given — the signing connector is unavailable
        var cause = new TspException(TspFailureInfo.SYSTEM_FAILURE, "signing failed", "signing connector error");
        when(formatter.formatDtbs(any(), any(), any(), any(), any(), any())).thenReturn(new byte[1]);
        when(signer.sign(any())).thenThrow(cause);

        // when / then
        var thrown = assertThrows(TspException.class,
                () -> generator.generate(aTspRequest().build(), aTimestampingProfile(),
                        mock(CertificateChain.class), BigInteger.ONE, Instant.now()));

        assertThat(thrown).isSameAs(cause);
    }

    @Test
    void generate_propagatesTspException_fromFormatterSigningResponse() throws Exception {
        // given — the formatter fails to assemble the final token from the signature
        var cause = new TspException(TspFailureInfo.SYSTEM_FAILURE, "cannot assemble token", "internal error");
        when(formatter.formatDtbs(any(), any(), any(), any(), any(), any())).thenReturn(new byte[1]);
        when(signer.sign(any())).thenReturn(new byte[1]);
        when(formatter.formatSigningResponse(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(cause);

        // when / then
        var thrown = assertThrows(TspException.class,
                () -> generator.generate(aTspRequest().build(), aTimestampingProfile(),
                        mock(CertificateChain.class), BigInteger.ONE, Instant.now()));

        assertThat(thrown).isSameAs(cause);
    }
}

package com.czertainly.core.service.tsa.certificateprovider;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.core.certificate.CertificateChainResponseDto;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateBuilder;
import com.czertainly.core.model.signing.scheme.DelegatedSigning;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.tsa.CertificateChain;
import com.czertainly.core.util.CertificateTestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticManagedKeyCertificateProviderTest {

    @Mock
    CertificateService certificateService;

    @InjectMocks
    StaticManagedKeyCertificateProvider provider;

    // ── validate() ───────────────────────────────────────────────────────────

    @Test
    void validate_returnsNok_whenSchemeIsNotStaticKeyManagedSigning() {
        // given
        var unsupportedScheme = new DelegatedSigning(UUID.randomUUID(), List.of());

        // when
        var result = provider.validate(unsupportedScheme, false);

        // then
        assertThat(result).isInstanceOf(ValidationResult.Nok.class);
        assertThat(((ValidationResult.Nok) result).failureInfo()).isEqualTo(TspFailureInfo.SYSTEM_FAILURE);
    }

    @Test
    void validate_returnsNok_whenCertificateIsNotAcceptableForNonQualifiedTimestamping() {
        // given — a revoked certificate is not acceptable for signing
        var certificate = CertificateBuilder.aCertificate().state(CertificateState.REVOKED).build();
        var scheme = new StaticKeyManagedSigning(certificate, List.of());

        // when
        var result = provider.validate(scheme, false);

        // then
        assertThat(result).isInstanceOf(ValidationResult.Nok.class);
        assertThat(((ValidationResult.Nok) result).failureInfo()).isEqualTo(TspFailureInfo.SYSTEM_FAILURE);
    }

    @Test
    void validate_returnsOk_whenCertificateIsAcceptableForNonQualifiedTimestamping() {
        // given
        var scheme = new StaticKeyManagedSigning(CertificateBuilder.valid(), List.of());

        // when
        var result = provider.validate(scheme, false);

        // then
        assertThat(result).isInstanceOf(ValidationResult.Ok.class);
    }

    @Test
    void validate_returnsNok_whenCertificateHasNoQcComplianceForQualifiedTimestamping() {
        // given — qcCompliance is absent, which is required for qualified timestamps (ETSI EN 319 421)
        var scheme = new StaticKeyManagedSigning(CertificateBuilder.valid(), List.of());

        // when
        var result = provider.validate(scheme, true);

        // then
        assertThat(result).isInstanceOf(ValidationResult.Nok.class);
        assertThat(((ValidationResult.Nok) result).failureInfo()).isEqualTo(TspFailureInfo.SYSTEM_FAILURE);
    }

    @Test
    void validate_returnsOk_whenCertificateIsAcceptableForQualifiedTimestamping() {
        // given
        var certificate = CertificateBuilder.aCertificate().qcCompliance(true).build();
        var scheme = new StaticKeyManagedSigning(certificate, List.of());

        // when
        var result = provider.validate(scheme, true);

        // then
        assertThat(result).isInstanceOf(ValidationResult.Ok.class);
    }

    // ── getCertificateChain() ─────────────────────────────────────────────────

    @Test
    void getCertificateChain_throwsTspException_whenSchemeIsNotStaticKeyManagedSigning() {
        // given
        var unsupportedScheme = new DelegatedSigning(UUID.randomUUID(), List.of());

        // when / then
        var exception = assertThrows(TspException.class, () -> provider.getCertificateChain(unsupportedScheme));
        assertThat(exception.getFailureInfo()).isEqualTo(TspFailureInfo.SYSTEM_FAILURE);
    }

    @Test
    void getCertificateChain_throwsTspException_whenCertificateIsNotFound() throws Exception {
        // given
        var certificate = certificateWithId();
        var scheme = new StaticKeyManagedSigning(certificate, List.of());
        when(certificateService.getCertificateChain(any(), anyBoolean()))
                .thenThrow(new com.czertainly.api.exception.NotFoundException(Certificate.class, certificate.uuid));

        // when / then
        var exception = assertThrows(TspException.class, () -> provider.getCertificateChain(scheme));
        assertThat(exception.getFailureInfo()).isEqualTo(TspFailureInfo.SYSTEM_FAILURE);
    }

    @Test
    void getCertificateChain_returnsCertificateChain_whenCertificateExists() throws Exception {
        // given
        X509Certificate x509 = CertificateTestUtil.createTimestampingCertificate();
        var chainResponseDto = chainResponseWithSingleCert(x509);

        var certificate = certificateWithId();
        var scheme = new StaticKeyManagedSigning(certificate, List.of());
        when(certificateService.getCertificateChain(any(), anyBoolean())).thenReturn(chainResponseDto);

        // when
        CertificateChain result = provider.getCertificateChain(scheme);

        // then
        assertThat(result.signingCertificate()).isEqualTo(x509);
        assertThat(result.chain()).isEqualTo(List.of(x509));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Certificate certificateWithId() {
        return CertificateBuilder.aCertificate().withoutKey().build();
    }

    private static CertificateChainResponseDto chainResponseWithSingleCert(X509Certificate x509) throws Exception {
        var certDto = new CertificateDetailDto();
        certDto.setCertificateContent(Base64.getEncoder().encodeToString(x509.getEncoded()));

        var response = new CertificateChainResponseDto();
        response.setCertificates(List.of(certDto));
        return response;
    }
}

package com.otilm.core.certificate.request;

import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.common.attribute.common.BaseAttribute;
import com.otilm.api.model.core.enums.CertificateRequestFormat;
import com.otilm.core.attribute.CsrAttributes;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.model.request.CertificateRequest;
import com.otilm.core.service.RaProfileCertificateRequestAttributeService;
import com.otilm.core.util.CertificateRequestUtils;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtocolRequestAttributeValidatorTest {

    private final RaProfileCertificateRequestAttributeService svc = mock(
            RaProfileCertificateRequestAttributeService.class);
    private final ProtocolRequestAttributeValidator validator = new ProtocolRequestAttributeValidator(svc);

    @Test
    void skipsValidation_whenSetIsEmpty() throws Exception {
        // given — a strict profile whose resolved set is empty
        when(svc.resolveIssueAttributeSet(any())).thenReturn(List.of());
        when(svc.resolveExternalCsrValidationStrict(any())).thenReturn(true);

        // when / then — nothing to validate, so even a bogus request is accepted
        assertThatCode(() -> validator.validate(mock(CertificateRequest.class), mock(RaProfile.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void throwsCertificateException_whenStrictResolutionFails() throws Exception {
        // given — a strict profile whose attribute set cannot be resolved; this is an availability failure,
        // not a client fault, so it must surface as a server-side CertificateException, not a policy violation
        when(svc.resolveIssueAttributeSet(any())).thenThrow(new NotFoundException("no set"));
        when(svc.resolveExternalCsrValidationStrict(any())).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> validator.validate(mock(CertificateRequest.class), mock(RaProfile.class)))
                .isInstanceOf(CertificateException.class)
                .isNotInstanceOf(RequestAttributePolicyViolationException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void skipsValidation_whenLenientResolutionFails() throws Exception {
        // given — a lenient profile whose attribute set cannot be resolved
        when(svc.resolveIssueAttributeSet(any())).thenThrow(new NotFoundException("no set"));
        when(svc.resolveExternalCsrValidationStrict(any())).thenReturn(false);

        // when / then — lenient tolerates the availability failure and proceeds
        assertThatCode(() -> validator.validate(mock(CertificateRequest.class), mock(RaProfile.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void wrapsUnexpectedParseFailureAsSafePolicyViolation() throws Exception {
        // given — a request whose parse blows up with a raw internal detail that must never reach the wire
        BaseAttribute definition = mock(BaseAttribute.class);
        when(svc.resolveIssueAttributeSet(any())).thenReturn(List.of(definition));
        when(svc.resolveExternalCsrValidationStrict(any())).thenReturn(true);
        var rawSecretMessage = "boom: raw internal ASN.1 parse detail that must never reach the wire";
        CertificateRequest request = mock(CertificateRequest.class);
        when(request.getSubject()).thenThrow(new IllegalArgumentException(rawSecretMessage));

        // when / then — the raw detail is replaced by a safe, fixed message
        assertThatThrownBy(() -> validator.validate(request, mock(RaProfile.class)))
                .isInstanceOf(RequestAttributePolicyViolationException.class)
                .hasMessage("Certificate request could not be processed for validation")
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).doesNotContain(rawSecretMessage);
                    assertThat(((RequestAttributePolicyViolationException) ex).getPolicyDetails()).isNotEmpty();
                });
    }

    @Test
    void returnsWarnings_whenLenientAndRequiredRdnMissing() throws Exception {
        // given — a lenient profile whose resolved set requires a CommonName, and a CSR that has none
        when(svc.resolveIssueAttributeSet(any())).thenReturn(List.of(CsrAttributes.commonNameAttribute()));
        when(svc.resolveExternalCsrValidationStrict(any())).thenReturn(false);

        // when
        List<String> warnings = validator.validate(csrWithSubject("O=Acme,C=US"), mock(RaProfile.class));

        // then — the warning reaches the caller instead of being dropped after the log line
        assertThat(warnings).anySatisfy(w -> assertThat(w).contains("Missing required mapped field"));
    }

    private static CertificateRequest csrWithSubject(String subjectDn) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        JcaPKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDn),
                keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        PKCS10CertificationRequest csr = builder.build(signer);
        return CertificateRequestUtils
                .createCertificateRequest(Base64.getEncoder().encodeToString(csr.getEncoded()),
                        CertificateRequestFormat.PKCS10);
    }
}

package com.otilm.core.certificate.request;

import com.otilm.api.exception.CertificateRequestException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.connector.v3.certificate.X509RequestContent;
import com.otilm.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.CertificateRequestEntity;
import com.otilm.core.model.request.CertificateRequest;
import com.otilm.core.util.CertificateRequestUtils;
import com.otilm.core.util.CertificateUtil;
import com.otilm.core.util.X509RequestContentRenderer;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.Extensions;

/**
 * Seeds the identity a successor inherits from the certificate it replaces — subject DN and SAN, no extensions — for
 * the structured renew wire and for the rekey CSR the platform builds itself.
 */
@Slf4j
public final class RenewContentSeeder {

    private RenewContentSeeder() {
    }

    /**
     * The typed identity for the structured renew wire: the CSR the operator supplied when there is one, and otherwise
     * the predecessor certificate. Empty when that identity cannot be carried in full, in which case the caller leaves
     * {@code requestContent} unset and the connector parses the CSR the wire already carries.
     */
    public static Optional<X509RequestContent> seed(Certificate oldCertificate, Certificate newCertificate,
            ClientCertificateRenewRequestDto request) {
        try {
            ParsedRequestContent parsed = operatorSuppliedCsr(newCertificate, request)
                    ? X509RequestContentParser.parse(storedCsr(newCertificate))
                    : X509RequestContentParser.parse(storedCertificate(oldCertificate));
            if (!parsed.unsupportedSans().isEmpty()) {
                log
                        .warn("Not seeding structured renew content: subject alternative name {} has no typed representation",
                                parsed.unsupportedSans());
                return Optional.empty();
            }
            return Optional.of(parsed.content());
        } catch (CertificateException | CertificateRequestException | RuntimeException e) {
            log.warn("Not seeding structured renew content: the identity to carry forward could not be read", e);
            return Optional.empty();
        }
    }

    /**
     * The SAN extension block a rekey CSR inherits, or null when the predecessor carries no SAN. Fails closed: the
     * platform builds this CSR itself, so nothing downstream can recover a SAN dropped here.
     */
    public static Extensions rekeySanExtensions(X509Certificate oldCertificate) {
        ParsedRequestContent parsed = X509RequestContentParser.parse(oldCertificate);
        if (!parsed.unsupportedSans().isEmpty()) {
            throw new ValidationException(
                    "The certificate carries a subject alternative name the platform cannot re-request: %s"
                            .formatted(String.join(", ", parsed.unsupportedSans())));
        }
        try {
            return X509RequestContentRenderer.toExtensions(parsed.content());
        } catch (IOException e) {
            throw new ValidationException(
                    "Failed to build the subject alternative name of the re-keyed request. Error: " + e.getMessage());
        }
    }

    private static boolean operatorSuppliedCsr(Certificate newCertificate, ClientCertificateRenewRequestDto request) {
        return request != null && request.getRequest() != null && newCertificate.getCertificateRequest() != null;
    }

    private static CertificateRequest storedCsr(Certificate certificate) throws CertificateRequestException {
        CertificateRequestEntity stored = certificate.getCertificateRequest();
        return CertificateRequestUtils
                .createCertificateRequest(stored.getContent(), stored.getCertificateRequestFormat());
    }

    private static X509Certificate storedCertificate(Certificate certificate) throws CertificateException {
        return CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent());
    }
}

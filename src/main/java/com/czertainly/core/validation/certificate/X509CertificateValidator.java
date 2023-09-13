package com.czertainly.core.validation.certificate;

import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateValidationDto;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.certificate.CertificateValidationStep;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.CrlUtil;
import com.czertainly.core.util.OcspUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service(CertificateValidator.X509)
public class X509CertificateValidator implements ICertificateValidator {
    private static final Logger logger = LoggerFactory.getLogger(X509CertificateValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DAYS_TO_EXPIRE = 30;

    private CertificateRepository certificateRepository;

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Override
    public CertificateStatus validateCertificate(Certificate certificate) {
        logger.debug("Initiating the certificate validation");
        // TODO: replace with refactored get certificate chain
//        List<Certificate> chainCerts = getCertificateChain(certificate);
        List<Certificate> certificateChain = List.of(certificate);

        int lastIndex = certificateChain.size() - 1;
        CertificateStatus previousCertStatus = CertificateStatus.UNKNOWN;
        boolean isCompleteChain = isTrustAnchor(certificateChain.get(lastIndex));
        Map<CertificateValidationStep, CertificateValidationDto> validationOutput;
        for (int i = lastIndex; i >= 0; i--) {
            if (i == lastIndex) {
                validationOutput = validatePathCertificate(certificateChain.get(i), null, null, isCompleteChain);
            } else {
                validationOutput = validatePathCertificate(certificateChain.get(i), certificateChain.get(i + 1), previousCertStatus, isCompleteChain);
            }

            CertificateStatus certificateStatus = calculateResultStatus(validationOutput, certificate.getStatus());
            finalizeValidation(certificate, previousCertStatus, validationOutput);

            previousCertStatus = certificateStatus;
        }

        return previousCertStatus;
    }

    private Map<CertificateValidationStep, CertificateValidationDto> validatePathCertificate(Certificate certificate, Certificate issuerCertificate, CertificateStatus issuerCertificateStatus, boolean isCompleteChain) {
        Map<CertificateValidationStep, CertificateValidationDto> validationOutput = getValidationInitialOutput();

        // 1) certificate chain check
        if (issuerCertificate == null) {
            // should be trust anchor (Root CA certificate)
            if (isCompleteChain) {
                validationOutput.put(CertificateValidationStep.CERTIFICATE_CHAIN, new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate chain is complete. Certificate is Root CA certificate (trusted anchor)."));
            } else {
                validationOutput.put(CertificateValidationStep.CERTIFICATE_CHAIN, new CertificateValidationDto(CertificateValidationStatus.FAILED, "Incomplete certificate chain. Issuer certificate is not available in the inventory or in the AIA extension."));
                validationOutput.put(CertificateValidationStep.SIGNATURE_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer certificate is not available."));
            }
            validationOutput.put(CertificateValidationStep.OCSP_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer certificate is not available."));
            validationOutput.put(CertificateValidationStep.CRL_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer certificate is not available."));
        } else {
            if (isCompleteChain) {
                validationOutput.put(CertificateValidationStep.CERTIFICATE_CHAIN, new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate chain is complete."));
            } else {
                validationOutput.put(CertificateValidationStep.CERTIFICATE_CHAIN, new CertificateValidationDto(CertificateValidationStatus.FAILED, "Incomplete certificate chain. Missing certificate in validation path."));
            }
        }

        // 2) prepare X509Certificate objects and verify signature based on issuer
        X509Certificate x509Certificate = null;
        X509Certificate x509IssuerCertificate = null;
        try {
            x509Certificate = CertificateUtil.getX509Certificate(certificate.getCertificateContent().getContent());
            if (issuerCertificate != null)
                x509IssuerCertificate = CertificateUtil.getX509Certificate(issuerCertificate.getCertificateContent().getContent());
        } catch (CertificateException e) {
            validationOutput.put(CertificateValidationStep.SIGNATURE_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.FAILED, x509Certificate == null ? "Certificate cannot be parsed." : "Issuer certificate cannot be parsed."));
            return validationOutput;
        }
        if (issuerCertificate != null) {
            if(isSelfSigned(certificate) || (!issuerCertificateStatus.equals(CertificateStatus.INVALID) && !issuerCertificateStatus.equals(CertificateStatus.REVOKED))) {
                validationOutput.put(CertificateValidationStep.SIGNATURE_VERIFICATION, checkCertificateSignature(x509Certificate, x509IssuerCertificate));
            }
            else {
                validationOutput.put(CertificateValidationStep.SIGNATURE_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.FAILED, "Signature verification failed. Issuer certificate is invalid or revoked"));
            }
        }

        // 3) check certificate validity
        validationOutput.put(CertificateValidationStep.CERTIFICATE_VALIDITY, checkCertificateValidity(x509Certificate));

        if (issuerCertificate != null) {
            // 4) check OCSP
            validationOutput.put(CertificateValidationStep.OCSP_VERIFICATION, checkOcspRevocationStatus(x509Certificate, x509IssuerCertificate));

            // 5) CRL check
            validationOutput.put(CertificateValidationStep.CRL_VERIFICATION, checkCrlRevocationStatus(x509Certificate));
        }

        return validationOutput;
    }

    private CertificateValidationDto checkCertificateSignature(X509Certificate x509Certificate, X509Certificate x509IssuerCertificate) {
        if (x509IssuerCertificate == null) { // self-signed
            if (verifySignature(x509Certificate, x509Certificate)) {
                return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Self-signed signature verification successful.");
            } else {
                return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Self-signed signature verification failed.");
            }
        } else {
            if (verifySignature(x509Certificate, x509IssuerCertificate)) {
                return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Signature verification successful.");
            } else {
                return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Signature verification failed.");
            }
        }
    }

    private CertificateValidationDto checkCertificateValidity(X509Certificate x509Certificate) {
        long millisToExpiry;
        Date currentUtcDate = Date.from(Instant.now());
        Date notAfterDate = x509Certificate.getNotAfter();
        Date notBeforeDate = x509Certificate.getNotBefore();
        if (notBeforeDate.after(currentUtcDate)) {
            return new CertificateValidationDto(CertificateValidationStatus.INVALID, "Certificate is inactive (not valid yet).");
        } else if (currentUtcDate.after(notAfterDate)) {
            return new CertificateValidationDto(CertificateValidationStatus.EXPIRED, "Certificate is expired.");
        } else if ((millisToExpiry = notAfterDate.getTime() - currentUtcDate.getTime()) < TimeUnit.DAYS.toMillis(DAYS_TO_EXPIRE)) {
            return new CertificateValidationDto(CertificateValidationStatus.EXPIRING, "Certificate will expire in " + convertMillisecondsToTimeString(millisToExpiry));
        } else {
            return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate is valid.");
        }
    }

    private CertificateValidationDto checkOcspRevocationStatus(X509Certificate x509Certificate, X509Certificate x509IssuerCertificate) {
        List<String> ocspUrls = OcspUtil.getOcspUrlFromCertificate(x509Certificate);
        if (ocspUrls.isEmpty()) {
            return new CertificateValidationDto(CertificateValidationStatus.WARNING, "No OCSP URL in certificate");
        } else {
            StringBuilder ocspMessage = new StringBuilder();
            CertificateValidationStatus ocspOutputStatus = CertificateValidationStatus.NOT_CHECKED;
            for (String ocspUrl : ocspUrls) {
                try {
                    CertificateValidationStatus ocspStatus = OcspUtil.checkOcsp(x509Certificate, x509IssuerCertificate, ocspUrl);
                    if (ocspStatus.equals(CertificateValidationStatus.SUCCESS)) {
                        if (ocspOutputStatus.equals(CertificateValidationStatus.NOT_CHECKED)) {
                            ocspOutputStatus = ocspStatus;
                        }
                        ocspMessage.append("OCSP verification successful from URL ");
                        ocspMessage.append(ocspUrl);
                    } else if (ocspStatus.equals(CertificateValidationStatus.REVOKED)) {
                        ocspOutputStatus = ocspStatus;
                        ocspMessage.append("Certificate was revoked according to information from OCSP URL ");
                        ocspMessage.append(ocspUrl);
                        break;
                    } else {
                        ocspOutputStatus = ocspStatus;
                        ocspMessage.append("OCSP Check result is unknown from URL ");
                        ocspMessage.append(ocspUrl);
                    }
                } catch (Exception e) {
                    logger.debug("Not able to check OCSP: {}", e.getMessage());
                    ocspOutputStatus = CertificateValidationStatus.WARNING;
                    ocspMessage.append("Error while checking OCSP URL ");
                    ocspMessage.append(ocspUrl);
                    ocspMessage.append(". Error: ");
                    ocspMessage.append(e.getLocalizedMessage());
                }
            }

            return new CertificateValidationDto(ocspOutputStatus, ocspMessage.toString());
        }
    }

    private CertificateValidationDto checkCrlRevocationStatus(X509Certificate x509Certificate) {
        List<String> crlUrls = List.of();
        try {
            crlUrls = CrlUtil.getCDPFromCertificate(x509Certificate);
        } catch (IOException e) {
            return new CertificateValidationDto(CertificateValidationStatus.WARNING, "Failed to retrieve CRL URL from certificate: " + e.getLocalizedMessage());
        }
        if (crlUrls.isEmpty()) {
            return new CertificateValidationDto(CertificateValidationStatus.WARNING, "No CRL URL in certificate");
        } else {
            String crlOutput = "";
            StringBuilder crlMessage = new StringBuilder();
            CertificateValidationStatus crlOutputStatus = CertificateValidationStatus.NOT_CHECKED;
            logger.debug("Checking for the CRL of the certificate {}", x509Certificate.getSubjectX500Principal().getName());
            for (String crlUrl : crlUrls) {
                try {
                    crlOutput = CrlUtil.checkCertificateRevocationList(x509Certificate, crlUrl);
                    if (crlOutput == null) {
                        if (crlOutputStatus.equals(CertificateValidationStatus.NOT_CHECKED)) {
                            crlOutputStatus = CertificateValidationStatus.SUCCESS;
                        }
                        crlMessage.append("CRL verification successful from URL ");
                        crlMessage.append(crlUrl);
                    } else {
                        crlOutputStatus = CertificateValidationStatus.REVOKED;
                        crlMessage.append("Certificate was revoked according to information from CRL URL ");
                        crlMessage.append(crlUrl);
                        crlMessage.append(". ");
                        crlMessage.append(crlOutput);
                        break;
                    }
                } catch (Exception e) {
                    logger.debug("Not able to check CRL: {}", e.getMessage());
                    crlOutputStatus = CertificateValidationStatus.WARNING;
                    crlMessage.append("Error while checking CRL URL ");
                    crlMessage.append(crlUrl);
                    crlMessage.append(". Error: ");
                    crlMessage.append(e.getLocalizedMessage());
                }
            }

            return new CertificateValidationDto(crlOutputStatus, crlMessage.toString());
        }
    }

    private CertificateStatus calculateResultStatus(Map<CertificateValidationStep, CertificateValidationDto> validationOutput, CertificateStatus originalCertificateStatus) {
        CertificateValidationDto certificateValidationDto = validationOutput.get(CertificateValidationStep.CERTIFICATE_CHAIN);
        if (!certificateValidationDto.getStatus().equals(CertificateValidationStatus.SUCCESS)) {
            return CertificateStatus.INVALID;
        }
        certificateValidationDto = validationOutput.get(CertificateValidationStep.SIGNATURE_VERIFICATION);
        if (!certificateValidationDto.getStatus().equals(CertificateValidationStatus.SUCCESS)) {
            return CertificateStatus.INVALID;
        }

        CertificateValidationDto validityCertificateValidationDto = validationOutput.get(CertificateValidationStep.CERTIFICATE_VALIDITY);
        if (validityCertificateValidationDto.getStatus().equals(CertificateValidationStatus.INVALID)) {
            return CertificateStatus.INVALID;
        }
        if (validityCertificateValidationDto.getStatus().equals(CertificateValidationStatus.EXPIRED)) {
            return CertificateStatus.EXPIRED;
        }

        CertificateValidationDto crlValidationDto = validationOutput.get(CertificateValidationStep.CRL_VERIFICATION);
        if (validationOutput.get(CertificateValidationStep.OCSP_VERIFICATION).getStatus().equals(CertificateValidationStatus.REVOKED)
                || crlValidationDto.getStatus().equals(CertificateValidationStatus.REVOKED)) {
            return CertificateStatus.REVOKED;
        }

        // check if that step is necessary to not override status of certificate that was freshly revoked
        // probably yes until unrevoke support is implemented
        if (originalCertificateStatus.equals(CertificateStatus.REVOKED)) {
            crlValidationDto.setMessage(crlValidationDto.getMessage() + " But certificate was revoked via platform and CRL may not be updated.");
            return CertificateStatus.REVOKED;
        }

        if (validityCertificateValidationDto.getStatus().equals(CertificateValidationStatus.EXPIRING)) {
            return CertificateStatus.EXPIRING;
        }

        return CertificateStatus.VALID;
    }

    private void finalizeValidation(Certificate certificate, CertificateStatus status, Map<CertificateValidationStep, CertificateValidationDto> validationOutput) {
        certificate.setStatus(status);
        try {
            certificate.setCertificateValidationResult(OBJECT_MAPPER.writeValueAsString(validationOutput));
            certificateRepository.save(certificate);
        } catch (Exception e) {
            logger.warn("Error in serialization of validation output for {}", certificate);
        }
    }

    private boolean isSelfSigned(Certificate certificate) {
        return certificate.getSubjectDn().equals(certificate.getIssuerDn());
    }

    private boolean isTrustAnchor(Certificate certificate) {
        return isSelfSigned(certificate);
    }

    private boolean verifySignature(X509Certificate subjectCertificate, X509Certificate issuerCertificate) {
        try {
            subjectCertificate.verify(issuerCertificate.getPublicKey());
            return true;
        } catch (Exception e) {
            logger.debug("Unable to verify certificate for signature", e);
            return false;
        }
    }

    private String convertMillisecondsToTimeString(long milliseconds) {
        final long dy = TimeUnit.MILLISECONDS.toDays(milliseconds);
        final long hr = TimeUnit.MILLISECONDS.toHours(milliseconds)
                - TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds));
        final long min = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
                - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds));

        return String.format("%d days %d hours %d minutes %d seconds", dy, hr, min, sec);
    }

    private Map<CertificateValidationStep, CertificateValidationDto> getValidationInitialOutput() {
        Map<CertificateValidationStep, CertificateValidationDto> validationOutput = new LinkedHashMap<>();
        validationOutput.put(CertificateValidationStep.CERTIFICATE_CHAIN, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationStep.SIGNATURE_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationStep.CERTIFICATE_VALIDITY, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationStep.OCSP_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationStep.CRL_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        return validationOutput;
    }
}

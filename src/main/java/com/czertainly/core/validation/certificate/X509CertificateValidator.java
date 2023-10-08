package com.czertainly.core.validation.certificate;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.*;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service("X.509")
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
    public CertificateStatus validateCertificate(Certificate certificate, boolean isCompleteChain) throws CertificateException {
        logger.debug("Initiating the certificate validation: {}", certificate);

        ArrayList<Certificate> certificateChain = new ArrayList<>();
        Certificate lastCertificate = certificate;
        do {
            certificateChain.add(lastCertificate);
            lastCertificate = lastCertificate.getIssuerCertificateUuid() == null ? null : certificateRepository.findByUuid(lastCertificate.getIssuerCertificateUuid()).orElse(null);
        } while (lastCertificate != null);

        X509Certificate x509Certificate;
        X509Certificate x509IssuerCertificate = null;
        CertificateStatus previousCertStatus = CertificateStatus.UNKNOWN;
        Map<CertificateValidationCheck, CertificateValidationDto> validationOutput;
        for (int i = certificateChain.size() - 1; i >= 0; i--) {
            // initialization by preparing X509Certificate object
            x509Certificate = CertificateUtil.getX509Certificate(certificateChain.get(i).getCertificateContent().getContent());

            boolean isEndCertificate = i == 0;
            validationOutput = validatePathCertificate(x509Certificate, x509IssuerCertificate, previousCertStatus, isCompleteChain, isEndCertificate);
            CertificateStatus certificateStatus = calculateResultStatus(validationOutput, certificateChain.get(i).getStatus());
            finalizeValidation(certificateChain.get(i), certificateStatus, validationOutput);

            previousCertStatus = certificateStatus;
            x509IssuerCertificate = x509Certificate;
        }

        logger.debug("Certificate validation of {} finalized with result: {}", certificate, previousCertStatus);
        return previousCertStatus;
    }

    private Map<CertificateValidationCheck, CertificateValidationDto> validatePathCertificate(X509Certificate certificate, X509Certificate issuerCertificate, CertificateStatus issuerCertificateStatus, boolean isCompleteChain, boolean isEndCertificate) {
        Map<CertificateValidationCheck, CertificateValidationDto> validationOutput = initializeValidationOutput();

        // check certificate signature
        // section (a)(1) in https://datatracker.ietf.org/doc/html/rfc5280#section-6.1.3
        validationOutput.put(CertificateValidationCheck.SIGNATURE_VERIFICATION, checkCertificateSignature(certificate, issuerCertificate, issuerCertificateStatus, isCompleteChain));

        // check certificate validity
        // section (a)(2) in https://datatracker.ietf.org/doc/html/rfc5280#section-6.1.3
        validationOutput.put(CertificateValidationCheck.CERTIFICATE_VALIDITY, checkCertificateValidity(certificate));

        // check if certificate is not revoked - OCSP & CRL
        // section (a)(3) in https://datatracker.ietf.org/doc/html/rfc5280#section-6.1.3
        validationOutput.put(CertificateValidationCheck.OCSP_VERIFICATION, checkOcspRevocationStatus(certificate, issuerCertificate));
        validationOutput.put(CertificateValidationCheck.CRL_VERIFICATION, checkCrlRevocationStatus(certificate, issuerCertificate));

        // check certificate issuer DN and if certificate chain is valid
        // section (a)(4) in https://datatracker.ietf.org/doc/html/rfc5280#section-6.1.3
        validationOutput.put(CertificateValidationCheck.CERTIFICATE_CHAIN, checkCertificateChain(certificate, issuerCertificate, isCompleteChain));

        // (k) and (l) section in https://datatracker.ietf.org/doc/html/rfc5280#section-6.1.4
        validationOutput.put(CertificateValidationCheck.BASIC_CONSTRAINTS, checkBasicConstraints(certificate, issuerCertificate, isEndCertificate));

        // (n) section in https://datatracker.ietf.org/doc/html/rfc5280#section-6.1.4
        validationOutput.put(CertificateValidationCheck.KEY_USAGE, checkKeyUsage(certificate));

        return validationOutput;
    }

    private CertificateValidationDto checkCertificateChain(X509Certificate certificate, X509Certificate issuerCertificate, boolean isCompleteChain) {
        if (issuerCertificate == null) {
            // should be trust anchor (Root CA certificate)
            if (isCompleteChain) {
                return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate chain is complete. Certificate is Root CA certificate (trusted anchor).");
            } else {
                return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Incomplete certificate chain. Issuer certificate is not available in the inventory or in the AIA extension.");
            }
        } else {
            String issuerNameEqualityMessage = "";
            if (!issuerCertificate.getSubjectX500Principal().getName().equals(certificate.getIssuerX500Principal().getName())) {
                issuerNameEqualityMessage = "Certificate issuer DN does not equal to issuer certificate subject DN.";
            }

            if (isCompleteChain) {
                if (issuerNameEqualityMessage.isEmpty()) {
                    return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate chain is complete.");
                } else {
                    return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Certificate chain is complete. " + issuerNameEqualityMessage);
                }
            } else {
                return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Incomplete certificate chain. Missing certificate in validation path. " + issuerNameEqualityMessage);
            }
        }
    }

    private CertificateValidationDto checkCertificateSignature(X509Certificate certificate, X509Certificate issuerCertificate, CertificateStatus issuerCertificateStatus, boolean isCompleteChain) {
//        if (issuerCertificateStatus.equals(CertificateStatus.INVALID) || issuerCertificateStatus.equals(CertificateStatus.REVOKED)) {
//            return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Signature verification failed. Issuer certificate is invalid or revoked");
//        }

        if (issuerCertificate == null) { // self-signed root CA
            if (!isCompleteChain) {
                return new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer certificate is not available.");
            }

            if (verifySignature(certificate, certificate)) {
                return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Self-signed signature verification successful.");
            } else {
                return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Self-signed signature verification failed.");
            }
        } else {
            if (verifySignature(certificate, issuerCertificate)) {
                return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Signature verification successful.");
            } else {
                return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Signature verification failed.");
            }
        }
    }

    private CertificateValidationDto checkCertificateValidity(X509Certificate certificate) {
        long millisToExpiry;
        Date currentUtcDate = Date.from(Instant.now());
        Date notAfterDate = certificate.getNotAfter();
        Date notBeforeDate = certificate.getNotBefore();
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

    private CertificateValidationDto checkOcspRevocationStatus(X509Certificate certificate, X509Certificate issuerCertificate) {
        if (issuerCertificate == null) {
            return new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer certificate is not available.");
        }

        List<String> ocspUrls = OcspUtil.getOcspUrlFromCertificate(certificate);
        if (ocspUrls.isEmpty()) {
            return new CertificateValidationDto(CertificateValidationStatus.WARNING, "No OCSP URL in certificate");
        }

        StringBuilder ocspMessage = new StringBuilder();
        CertificateValidationStatus ocspOutputStatus = CertificateValidationStatus.NOT_CHECKED;
        for (String ocspUrl : ocspUrls) {
            try {
                CertificateValidationStatus ocspStatus = OcspUtil.checkOcsp(certificate, issuerCertificate, ocspUrl);
                if (ocspStatus.equals(CertificateValidationStatus.SUCCESS)) {
                    if (ocspOutputStatus.equals(CertificateValidationStatus.NOT_CHECKED)) {
                        ocspOutputStatus = ocspStatus;
                    }
                    ocspMessage.append("OCSP verification successful from URL ");
                    ocspMessage.append(ocspUrl);
                    ocspMessage.append(". ");
                } else if (ocspStatus.equals(CertificateValidationStatus.REVOKED)) {
                    ocspOutputStatus = ocspStatus;
                    ocspMessage.append("Certificate was revoked according to information from OCSP URL ");
                    ocspMessage.append(ocspUrl);
                    ocspMessage.append(". ");
                    break;
                } else {
                    ocspOutputStatus = ocspStatus;
                    ocspMessage.append("OCSP Check result is unknown from URL ");
                    ocspMessage.append(ocspUrl);
                    ocspMessage.append(". ");
                }
            } catch (Exception e) {
                logger.debug("Not able to check OCSP: {}", e.getMessage());
                ocspOutputStatus = CertificateValidationStatus.WARNING;
                ocspMessage.append("Error while checking OCSP URL ");
                ocspMessage.append(ocspUrl);
                ocspMessage.append(". Error: ");
                ocspMessage.append(e.getLocalizedMessage());
                ocspMessage.append(". ");
            }
        }

        return new CertificateValidationDto(ocspOutputStatus, ocspMessage.toString());
    }

    private CertificateValidationDto checkCrlRevocationStatus(X509Certificate certificate, X509Certificate issuerCertificate) {
        if (issuerCertificate == null) {
            return new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer certificate is not available.");
        }

        List<String> crlUrls;
        try {
            crlUrls = CrlUtil.getCDPFromCertificate(certificate);
        } catch (IOException e) {
            return new CertificateValidationDto(CertificateValidationStatus.WARNING, "Failed to retrieve CRL URL from certificate: " + e.getLocalizedMessage());
        }

        if (crlUrls.isEmpty()) {
            return new CertificateValidationDto(CertificateValidationStatus.WARNING, "No CRL URL in certificate");
        }

        String crlOutput = "";
        StringBuilder crlMessage = new StringBuilder();
        CertificateValidationStatus crlOutputStatus = CertificateValidationStatus.NOT_CHECKED;
        logger.debug("Checking for the CRL of the certificate {}", certificate.getSubjectX500Principal().getName());
        for (String crlUrl : crlUrls) {
            try {
                crlOutput = CrlUtil.checkCertificateRevocationList(certificate, crlUrl);
                if (crlOutput == null) {
                    if (crlOutputStatus.equals(CertificateValidationStatus.NOT_CHECKED)) {
                        crlOutputStatus = CertificateValidationStatus.SUCCESS;
                    }
                    crlMessage.append("CRL verification successful from URL ");
                    crlMessage.append(crlUrl);
                    crlMessage.append(". ");
                } else {
                    crlOutputStatus = CertificateValidationStatus.REVOKED;
                    crlMessage.append("Certificate was revoked according to information from CRL URL ");
                    crlMessage.append(crlUrl);
                    crlMessage.append(". ");
                    crlMessage.append(crlOutput);
                    crlMessage.append(". ");
                    break;
                }
            } catch (Exception e) {
                logger.debug("Not able to check CRL: {}", e.getMessage());
                crlOutputStatus = CertificateValidationStatus.WARNING;
                crlMessage.append("Error while checking CRL URL ");
                crlMessage.append(crlUrl);
                crlMessage.append(". Error: ");
                crlMessage.append(e.getLocalizedMessage());
                crlMessage.append(". ");
            }
        }

        return new CertificateValidationDto(crlOutputStatus, crlMessage.toString());
    }

    private CertificateValidationDto checkBasicConstraints(X509Certificate certificate, X509Certificate issuerCertificate, boolean isEndCertificate) {
        int pathLenConstraint = certificate.getBasicConstraints();
        boolean isCa = pathLenConstraint >= 0;

        if (!isCa) {
            if (certificate.getVersion() == 3 && !isEndCertificate) {
                return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Certificate is not last in chain and is not marked as CA");
            } else if (certificate.getVersion() != 3) {
                return new CertificateValidationDto(CertificateValidationStatus.WARNING, "Certificate is not last in chain and cannot verify if it is a CA certificate");
            }
        } else if (issuerCertificate != null && pathLenConstraint > issuerCertificate.getBasicConstraints()) {
            return new CertificateValidationDto(CertificateValidationStatus.WARNING, "Certificate path length is greater than path length in issuer certificate");
        }

        return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate basic constraints verification successful.");
    }

    private CertificateValidationDto checkKeyUsage(X509Certificate certificate) {
        boolean isCa = certificate.getBasicConstraints() >= 0;

        if (!isCa) {
            return new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Certificate is not CA.");
        }

        if (CertificateUtil.isKeyUsagePresent(certificate.getKeyUsage(), CertificateUtil.KEY_USAGE_KEY_CERT_SIGN)) {
            return new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate keyCertSign bit is set and can be used to verify signatures on other certificates.");
        } else {
            return new CertificateValidationDto(CertificateValidationStatus.FAILED, "Certificate keyCertSign bit is not set and cannot be used to verify signatures on other certificates.");
        }
    }

    private CertificateStatus calculateResultStatus(Map<CertificateValidationCheck, CertificateValidationDto> validationOutput, CertificateStatus originalCertificateStatus) {
        CertificateValidationDto certificateValidationDto = validationOutput.get(CertificateValidationCheck.CERTIFICATE_CHAIN);
        if (!certificateValidationDto.getStatus().equals(CertificateValidationStatus.SUCCESS)) {
            return CertificateStatus.INVALID;
        }
        certificateValidationDto = validationOutput.get(CertificateValidationCheck.SIGNATURE_VERIFICATION);
        if (!certificateValidationDto.getStatus().equals(CertificateValidationStatus.SUCCESS)) {
            return CertificateStatus.INVALID;
        }

        CertificateValidationDto validityCertificateValidationDto = validationOutput.get(CertificateValidationCheck.CERTIFICATE_VALIDITY);
        if (validityCertificateValidationDto.getStatus().equals(CertificateValidationStatus.INVALID)) {
            return CertificateStatus.INVALID;
        }
        if (validityCertificateValidationDto.getStatus().equals(CertificateValidationStatus.EXPIRED)) {
            return CertificateStatus.EXPIRED;
        }

        CertificateValidationDto crlValidationDto = validationOutput.get(CertificateValidationCheck.CRL_VERIFICATION);
        if (validationOutput.get(CertificateValidationCheck.OCSP_VERIFICATION).getStatus().equals(CertificateValidationStatus.REVOKED)
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

    private void finalizeValidation(Certificate certificate, CertificateStatus status, Map<CertificateValidationCheck, CertificateValidationDto> validationOutput) throws CertificateException {
        certificate.setStatus(status);
        certificate.setStatusValidationTimestamp(LocalDateTime.now());
        try {
            certificate.setCertificateValidationResult(OBJECT_MAPPER.writeValueAsString(validationOutput));
            certificateRepository.save(certificate);
        } catch (Exception e) {
            throw new CertificateException("Error in serialization of validation output for " + certificate);
        }
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

    private Map<CertificateValidationCheck, CertificateValidationDto> initializeValidationOutput() {
        Map<CertificateValidationCheck, CertificateValidationDto> validationOutput = new LinkedHashMap<>();
        validationOutput.put(CertificateValidationCheck.CERTIFICATE_CHAIN, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationCheck.SIGNATURE_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationCheck.CERTIFICATE_VALIDITY, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationCheck.OCSP_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationCheck.CRL_VERIFICATION, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationCheck.BASIC_CONSTRAINTS, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        validationOutput.put(CertificateValidationCheck.KEY_USAGE, new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, null));
        return validationOutput;
    }
}

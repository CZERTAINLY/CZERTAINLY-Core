package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.certificate.CertificateValidationDto;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.CrlUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.OcspUtil;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR", "ROLE_CLIENT", "ROLE_ACME"})
public class CertValidationServiceImpl implements CertValidationService {
    private static final Logger logger = LoggerFactory.getLogger(CertValidationServiceImpl.class);

    private static final int DAYS_TO_EXPIRE = 30;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateRepository certificateRepository;

    @Override
    @Async("threadPoolTaskExecutor")
    public void validateAllCertificates() {
        List<Certificate> certificates = certificateRepository.findByStatus(CertificateStatus.UNKNOWN);
        for (Certificate certificate : certificates) {
            try {
                validate(certificate);
            } catch (Exception e) {
                logger.warn("Unable to validate the certificate {}", certificate.toString());
            }
        }
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void validateCertificates(List<Certificate> certificates) {
        for (Certificate certificate : certificates) {
            try {
                validate(certificate);
            } catch (Exception e) {
                logger.warn("Unable to validate the certificate {}", certificate.toString());
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.VALIDATE)
    public void validate(Certificate certificate) throws NotFoundException, CertificateException, IOException {
        logger.debug("Initiating the certificate validation");
        List<Certificate> chainCerts = getCertificateChain(certificate);
        for (int i = 0; i < chainCerts.size(); i++) {
            Certificate crt = chainCerts.get(i);
            if (crt.getSubjectDn().equals(crt.getIssuerDn())) {
                checkSelfSignedCertificate(crt);
            } else {
                if (checkFullChain(chainCerts)) {
                    if (chainCerts.size() > i + 1) {
                        certificateValidation(crt, chainCerts.get(i + 1));
                    }
                } else {
                    if (downloadUploadChain(certificate)) {
                        reValidate(certificate);
                    } else {
                        if (chainCerts.size() > i + 1) {
                            certificateValidation(crt, chainCerts.get(i + 1), true);
                        } else {
                            logger.warn("Incomplete Chain");
                            certificateValidation(crt, null, true);
                        }
                    }
                }
            }
        }
    }

    private Boolean checkFullChain(List<Certificate> certificates) {
        Certificate lastCert = certificates.get(certificates.size() - 1);
        if (lastCert.getSubjectDn().equals(lastCert.getIssuerDn())) {
            return true;
        }
        return false;
    }

    private void reValidate(Certificate certificate) throws NotFoundException, CertificateException, IOException {
        logger.debug("Initiating the certificate validation");
        List<Certificate> chainCerts = getCertificateChain(certificate);
        for (int i = 0; i < chainCerts.size(); i++) {
            Certificate crt = chainCerts.get(i);
            if (crt.getSubjectDn().equals(crt.getIssuerDn())) {
                checkSelfSignedCertificate(crt);
            } else {
                if (checkFullChain(chainCerts)) {
                    if (chainCerts.size() > i + 1) {
                        certificateValidation(crt, chainCerts.get(i + 1));
                    } else {
                        checkSelfSignedCertificate(crt);
                    }

                }
            }
        }
    }

    private boolean downloadUploadChain(Certificate certificate) {
        List<String> chainCertificates = downloadChainFromAia(certificate);
        List<Certificate> uploadedCertificate = new ArrayList<>();
        if (chainCertificates.isEmpty()) {
            return false;
        }

        for (String cert : chainCertificates) {
            try {
                uploadedCertificate.add(certificateService.checkCreateCertificate(cert));
            } catch (Exception e) {
                logger.error("Chain already exists");
            }
        }

        if (!uploadedCertificate.isEmpty()) {
            certificateService.updateIssuer();
            return true;
        } else {
            return false;
        }
    }

    private List<String> downloadChainFromAia(Certificate certificate) {
        List<String> chainCertificates = new ArrayList<>();
        String oldChainUrl = "";
        String chainUrl = "";
        try {
            X509Certificate certX509 = getX509(certificate.getCertificateContent().getContent());
            while (true) {
                chainUrl = OcspUtil.getChainFromAia(certX509);
                if (oldChainUrl.equals(chainUrl)) {
                    break;
                }
                oldChainUrl = chainUrl;
                if (chainUrl == null || chainUrl.isEmpty()) {
                    break;
                }
                String chainContent = downloadChain(chainUrl);
                if (chainContent.equals("")) {
                    break;
                }
                chainCertificates.add(chainContent);
                certX509 = getX509(chainContent);
            }

        } catch (Exception e) {
            logger.warn("Unable to get the chain of certificate from Authority Information Access");
        }
        return chainCertificates;
    }

    private String downloadChain(String chainUrl) {
        try {
            URL url = new URL(chainUrl);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(1000);
            urlConnection.setReadTimeout(1000);
            String fileName = chainUrl.split("/")[chainUrl.split("/").length - 1];
            try (InputStream in = url.openStream();
                 ReadableByteChannel rbc = Channels.newChannel(in);
                 FileOutputStream fos = new FileOutputStream(fileName)) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            } catch (Exception e) {
                logger.error(e.getMessage());
                return "";
            }
            CertificateFactory fac = CertificateFactory.getInstance("X509");
            FileInputStream is = new FileInputStream(fileName);
            X509Certificate cert = (X509Certificate) fac.generateCertificate(is);
            final StringWriter writer = new StringWriter();
            final JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
            pemWriter.writeObject(cert);
            pemWriter.flush();
            pemWriter.close();
            writer.close();
            is.close();
            Path path = Paths.get(fileName);
            Files.deleteIfExists(path);
            return writer.toString();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return "";
    }

    private ASN1Primitive toDERObject(byte[] data) throws IOException {
        ByteArrayInputStream inStream = new ByteArrayInputStream(data);
        ASN1InputStream asnInputStream = new ASN1InputStream(inStream);

        return asnInputStream.readObject();
    }

    private String Asn1ConversionChecker(byte[] rawData) {
        try {
            ASN1Primitive derObject = toDERObject(rawData);
            if (derObject instanceof DEROctetString) {
                DEROctetString derOctetString = (DEROctetString) derObject;

                derObject = toDERObject(derOctetString.getOctets());
                if (derObject instanceof ASN1String) {
                    ASN1String s = (ASN1String) derObject;
                    return s.getString();
                } else {
                    return new String(rawData);
                }

            }
            return new String(rawData);
        } catch (IOException e) {
            return new String(rawData);
        }
    }

    private void checkSelfSignedCertificate(Certificate certificate) {
        Map<String, CertificateValidationDto> validationOutput = getValidationInitialOutput();
        X509Certificate x509 = null;
        try {
            x509 = getX509(certificate.getCertificateContent().getContent());
        } catch (CertificateException e) {
            return;
        }
        boolean isValid = validateNotBefore(new Date(), x509.getNotBefore());
        long validTill = validateNotAfter(new Date(), x509.getNotAfter());

        try {
            x509.verify(x509.getPublicKey());
            certificate.setStatus(CertificateStatus.VALID);
            validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Signature verification completed successfully"));
        } catch (Exception e) {
            logger.error("Unable to verify the self-signed certificate signature", e);
            validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.FAILED, "Unable to verify the signature"));
        }

        CertificateStatus status;
        if (!isValid) {
            status = CertificateStatus.INVALID;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.INVALID, "Not Valid yet"));
            certificate.setStatus(status);
            certificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
            certificateRepository.save(certificate);
            return;
        }
        if (validTill < TimeUnit.DAYS.toMillis(DAYS_TO_EXPIRE) && validTill > 0) {
            status = CertificateStatus.EXPIRING;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.EXPIRING, "Expiring in " + convertMillisecondsToTimeString(validTill) ));
        }
        if (validTill > TimeUnit.DAYS.toMillis(DAYS_TO_EXPIRE)) {
            status = CertificateStatus.VALID;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Certificate validity check completed successfully"));
        }
        if (validTill <= 0) {
            status = CertificateStatus.EXPIRED;
            validationOutput.put("Certificate Expiry", new CertificateValidationDto(CertificateValidationStatus.EXPIRED, "Certificate expired " + convertMillisecondsToTimeString(validTill*-1) + " ago"));
            certificate.setStatus(status);
            certificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
            certificateRepository.save(certificate);
            return;
        }
        validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Self-signed Certificate"));
        validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Self-signed Certificate"));
        certificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
        certificateRepository.save(certificate);
    }

    private boolean verifySignature(X509Certificate subjectCertificate, X509Certificate issuerCertificate) {
        try {
            subjectCertificate.verify(issuerCertificate.getPublicKey());
            return true;
        } catch (Exception e) {
            logger.warn("Unable to verify certificate for signature", e);
            return false;
        }
    }

    @Override
    public List<Certificate> getCertificateChain(Certificate certificate) {
        List<Certificate> chainCerts = new ArrayList<>();
        chainCerts.add(certificate);
        int previousLength = 1;
        while (true) {
            checkAddCertificateToChain(chainCerts);
            if (chainCerts.size() != previousLength) {
                previousLength += 1;
            } else {
                break;
            }
        }
        return chainCerts;
    }

    private List<Certificate> checkAddCertificateToChain(List<Certificate> chainCertificates) {
        Certificate toCheckCertificate = chainCertificates.get(chainCertificates.size() - 1);
        if (toCheckCertificate.getIssuerSerialNumber() != null
                && toCheckCertificate.getIssuerSerialNumber().length() > 5) {
            try {
                chainCertificates.add(
                        certificateService.getCertificateEntityBySerial(toCheckCertificate.getIssuerSerialNumber()));
            } catch (NotFoundException e) {
                logger.error("Unable to find the chain of {}", toCheckCertificate.getCommonName());
            } catch (NullPointerException e) {
                logger.debug("Received end of chain");
            }
        }
        return chainCertificates;
    }

    private void certificateValidation(Certificate subjectCertificate, Certificate issuerCertificate)
            throws IOException, CertificateException {
        CertificateStatus status = CertificateStatus.UNKNOWN;

        X509Certificate certX509 = getX509(subjectCertificate.getCertificateContent().getContent());
        X509Certificate x509Issuer = getX509(issuerCertificate.getCertificateContent().getContent());
        List<String> crlUrls = CrlUtil.getCDPFromCertificate(certX509);
        List<String> ocspUrls = OcspUtil.getOcspUrlFromCertificate(certX509);

        boolean isValid = validateNotBefore(new Date(), certX509.getNotBefore());
        long validTill = validateNotAfter(new Date(), certX509.getNotAfter());

        CertificateStatus subjectCertificateOriginalStatus = subjectCertificate.getStatus();

        // Validation Process
        Map<String, CertificateValidationDto> validationOutput = getValidationInitialOutput();

        if (verifySignature(certX509, x509Issuer)) {
            validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Signature verification success"));
        } else {
            validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.FAILED, "Signature verification failed"));
            status = CertificateStatus.INVALID;
            subjectCertificate.setStatus(status);
            subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
            certificateRepository.save(subjectCertificate);
            return;
        }

        if (!isValid) {
            status = CertificateStatus.INVALID;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.INVALID, "Not valid yet"));
            subjectCertificate.setStatus(status);
            subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
            certificateRepository.save(subjectCertificate);
            return;
        }
        if (validTill < TimeUnit.DAYS.toMillis(DAYS_TO_EXPIRE) && validTill > 0) {
            status = CertificateStatus.EXPIRING;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.EXPIRING,
                    "Expiring within " + convertMillisecondsToTimeString(validTill) ));
        }
        if (validTill > TimeUnit.DAYS.toMillis(DAYS_TO_EXPIRE)) {
            status = CertificateStatus.VALID;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.SUCCESS,
                    "Certificate expiry status check successful"));
        }
        if (validTill <= 0) {
            status = CertificateStatus.EXPIRED;
            validationOutput.put("Certificate Expiry", new CertificateValidationDto(CertificateValidationStatus.EXPIRED,
                    "Certificate expired " + convertMillisecondsToTimeString(validTill*-1) + " ago"));
            subjectCertificate.setStatus(status);
            subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
            certificateRepository.save(subjectCertificate);
            return;
        }
        if (ocspUrls.isEmpty()) {
            if (status != CertificateStatus.EXPIRING) {
                status = CertificateStatus.VALID;
            }
            validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.WARNING, "No OCSP URL in certificate"));
        } else {
            try {
                Boolean isRevoked = false;
                String ocspOutput = "";
                String ocspMessage = "";
                for (String ocspUrl : ocspUrls) {
                    String ocspStatus = OcspUtil.checkOcsp(certX509, x509Issuer,
                            ocspUrl);
                    if (ocspStatus.equals("Success")) {
                        ocspOutput = "Success";
                        ocspMessage += "OCSP verification success from " + ocspUrl;
                    } else if (ocspStatus.equals("Failed")) {
                        isRevoked = true;
                        ocspOutput = "Failed";
                        ocspMessage += "Certificate was revoked according to information from OCSP.\nOCSP URL: " + ocspUrl;
                        break;
                    } else {
                        ocspOutput = "Unknown";
                        ocspMessage += "OCSP check result is unknown.\nOCSP URL: " + ocspUrl;

                    }
                }
                if (ocspOutput.equals("Success")) {
                    if (status != CertificateStatus.EXPIRING) {
                        status = CertificateStatus.VALID;
                    }
                    validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.SUCCESS,
                            ocspMessage));
                } else if (ocspOutput.equals("Failed")) {
                    status = CertificateStatus.REVOKED;
                    validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.REVOKED,
                            ocspMessage));
                    subjectCertificate.setStatus(status);
                    subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
                    certificateRepository.save(subjectCertificate);
                    return;
                } else {
                    validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.WARNING, ocspMessage));
                }

            } catch (Exception e) {
                logger.warn("Not able to check OCSP: {}", e.getMessage());
                validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.FAILED, "Error while checking OCSP.\nOCSP URL: " + String.join("\n", ocspUrls)));
            }
        }

        if (crlUrls.isEmpty()) {
            if (status != CertificateStatus.EXPIRING && !subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
                status = CertificateStatus.VALID;
            }
            validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.WARNING, "No CRL URL in certificate"));
        } else {
            boolean isRevoked = false;
            String crlOutput = "";
            for (String crlUrl : crlUrls) {
                try {
                    crlOutput = CrlUtil.checkCertificateRevocationList(certX509, crlUrl);
                    if (!crlOutput.equals("")) {
                        isRevoked = true;
                        break;
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
            if (isRevoked) {
                status = CertificateStatus.REVOKED;
                validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.REVOKED, "Certificate was revoked on "
                        + crlOutput.split("=")[1] + " according to the CRL. \n" + " Reason: " +
                        crlOutput.split("=")[0] + ".\n CRL URL(s): " + String.join(", ", crlUrls)));
                subjectCertificate.setStatus(status);
                subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
                certificateRepository.save(subjectCertificate);
                return;
            } else {
                if (status != CertificateStatus.EXPIRING && !subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
                    status = CertificateStatus.VALID;
                }
                if (subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
                    validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.REVOKED,
                            "Certificate revoked via platform. CRL returns valid. CRL may not be updated.\nCRL URL(s): " + String.join(", ", crlUrls)));
                    status = CertificateStatus.REVOKED;
                } else {
                    validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.SUCCESS,
                            "CRL verification completed successfully.\nCRL URL(s): " + String.join(", ", crlUrls)));
                }
            }
        }
        validationOutput.forEach((key, value) -> logger.debug(key + ":" + value));

        if (status.equals(CertificateStatus.REVOKED)) {
            for (Certificate cert : certificateRepository.findAllByIssuerSerialNumber(subjectCertificate.getSerialNumber())) {
                cert.setStatus(CertificateStatus.REVOKED);
                certificateRepository.save(cert);
            }
        }

        if (!subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
            subjectCertificate.setStatus(status);
        }
        subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
        certificateRepository.save(subjectCertificate);
    }

    private void certificateValidation(Certificate subjectCertificate, Certificate issuerCertificate, Boolean isIncomplete)
            throws IOException, CertificateException {
        CertificateStatus status = CertificateStatus.UNKNOWN;

        X509Certificate certX509 = getX509(subjectCertificate.getCertificateContent().getContent());
        X509Certificate x509Issuer = null;
        if (issuerCertificate != null) {
            x509Issuer = getX509(issuerCertificate.getCertificateContent().getContent());
            ;
        }
        List<String> crlUrls = CrlUtil.getCDPFromCertificate(certX509);
        List<String> ocspUrls = OcspUtil.getOcspUrlFromCertificate(certX509);

        boolean isValid = validateNotBefore(new Date(), certX509.getNotBefore());
        long validTill = validateNotAfter(new Date(), certX509.getNotAfter());

        CertificateStatus subjectCertificateOriginalStatus = subjectCertificate.getStatus();

        // Validation Process
        Map<String, CertificateValidationDto> validationOutput = getValidationInitialOutput();
        validationOutput.put("Certificate Chain", new CertificateValidationDto(CertificateValidationStatus.WARNING, "Issuer certificate cannot be found. It is unavailable in the inventory and in the AIA extension"));
        if (issuerCertificate == null) {
            validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer information unavailable"));
        } else {
            if (verifySignature(certX509, x509Issuer)) {
                validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.SUCCESS, "Signature verification successful"));
            } else {
                validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.FAILED, "Signature verification failed"));
                status = CertificateStatus.INVALID;
                subjectCertificate.setStatus(status);
                subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
                certificateRepository.save(subjectCertificate);
                return;
            }
        }

        if (!isValid) {
            status = CertificateStatus.INVALID;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.INVALID, "Not valid yet"));
            subjectCertificate.setStatus(status);
            subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
            certificateRepository.save(subjectCertificate);
            return;
        }
        if (validTill <= TimeUnit.DAYS.toMillis(DAYS_TO_EXPIRE) && validTill > 0) {
            status = CertificateStatus.EXPIRING;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.EXPIRING,
                    "Expiring within " + convertMillisecondsToTimeString(validTill) ));
        }
        if (validTill > TimeUnit.DAYS.toMillis(DAYS_TO_EXPIRE)) {
            status = CertificateStatus.VALID;
            validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.SUCCESS,
                    "Certificate expiry status check successful"));
        }
        if (validTill < 0) {
            status = CertificateStatus.EXPIRED;
            validationOutput.put("Certificate Expiry", new CertificateValidationDto(CertificateValidationStatus.EXPIRED,
                    "Certificate expired " + convertMillisecondsToTimeString(validTill*-1) + " ago"));
            subjectCertificate.setStatus(status);
            subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
            certificateRepository.save(subjectCertificate);
            return;
        }
        if (ocspUrls.isEmpty()) {
            if (status != CertificateStatus.EXPIRING) {
                status = CertificateStatus.VALID;
            }
            validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.WARNING, "No OCSP URL in certificate"));
        } else if (x509Issuer == null) {
            validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, "Issuer information unavailable"));
        } else {
            try {
                Boolean isRevoked = false;
                String ocspOutput = "";
                String ocspMessage = "";
                for (String ocspUrl : ocspUrls) {
                    String ocspStatus = OcspUtil.checkOcsp(certX509, x509Issuer,
                            ocspUrl);
                    if (ocspStatus.equals("Success")) {
                        ocspOutput = "Success";
                        ocspMessage += "OCSP verification successful from " + ocspUrl;
                    } else if (ocspStatus.equals("Failed")) {
                        isRevoked = true;
                        ocspOutput = "Failed";
                        ocspMessage += "Certificate was revoked according to information from OCSP.\nOCSP URL: " + ocspUrl;
                        break;
                    } else {
                        ocspOutput = "Unknown";
                        ocspMessage += "OCSP Check result is unknown.\nOCSP URL: " + ocspUrl;

                    }

                }
                if (ocspOutput.equals("Success")) {
                    if (status != CertificateStatus.EXPIRING) {
                        status = CertificateStatus.VALID;
                    }
                    validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.SUCCESS,
                            ocspMessage));
                } else if (ocspOutput.equals("Failed")) {
                    status = CertificateStatus.REVOKED;
                    validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.REVOKED,
                            ocspMessage));
                    subjectCertificate.setStatus(status);
                    subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
                    certificateRepository.save(subjectCertificate);
                    return;
                } else {
                    validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.WARNING, ocspMessage));
                }

            } catch (Exception e) {
                logger.warn("Not able to check OCSP: {}", e.getMessage());
                validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.FAILED, "Error while checking OCSP.\nOCSP URL: " + String.join("\n", ocspUrls)));
            }
        }

        if (crlUrls.isEmpty()) {
            if (status != CertificateStatus.EXPIRING && !subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
                status = CertificateStatus.VALID;
            }
            validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.WARNING, "No CRL URL in certificate"));
        } else {
            boolean isRevoked = false;
            String crlOutput = "";
            for (String crlUrl : crlUrls) {
                try {
                    crlOutput = CrlUtil.checkCertificateRevocationList(certX509, crlUrl);
                    if (!crlOutput.equals("")) {
                        isRevoked = true;
                        break;
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
            if (isRevoked) {
                status = CertificateStatus.REVOKED;
                validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.REVOKED, "Certificate was revoked on "
                        + crlOutput.split("=")[1] + " according to the CRL. \n" + " Reason: " +
                        crlOutput.split("=")[0] + ".\n CRL URL(s): " + String.join(", ", crlUrls)));
                subjectCertificate.setStatus(status);
                subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
                certificateRepository.save(subjectCertificate);
                return;
            } else {
                if (status != CertificateStatus.EXPIRING && !subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
                    status = CertificateStatus.VALID;
                }
                if (subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
                    validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.REVOKED,
                            "Certificate revoked via platform. CRL returns valid. CRL may not be updated.\nCRL URL(s): " + String.join(", ", crlUrls)));
                    status = CertificateStatus.REVOKED;
                } else {
                    validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.SUCCESS,
                            "CRL verification completed successfully.\nCRL URL(s): " + String.join(", ", crlUrls)));
                }
            }
        }
        validationOutput.forEach((key, value) -> logger.debug(key + ":" + value));

        if (status.equals(CertificateStatus.REVOKED)) {
            for (Certificate cert : certificateRepository.findAllByIssuerSerialNumber(subjectCertificate.getSerialNumber())) {
                cert.setStatus(CertificateStatus.REVOKED);
                certificateRepository.save(cert);
            }
        }

        if (!subjectCertificateOriginalStatus.equals(CertificateStatus.REVOKED)) {
            subjectCertificate.setStatus(status);
        }
        subjectCertificate.setCertificateValidationResult(MetaDefinitions.serializeValidation(validationOutput));
        certificateRepository.save(subjectCertificate);
    }

    private Map<String, CertificateValidationDto> getValidationInitialOutput() {

        Map<String, CertificateValidationDto> validationOutput = new LinkedHashMap<>();
        validationOutput.put("Signature Verification", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, ""));
        validationOutput.put("Certificate Validity", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, ""));
        validationOutput.put("OCSP Verification", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, ""));
        validationOutput.put("CRL Verification", new CertificateValidationDto(CertificateValidationStatus.NOT_CHECKED, ""));
        return validationOutput;
    }

    private X509Certificate getX509(String certificate) throws CertificateException {
        return CertificateUtil.getX509Certificate(certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", ""));
    }

    private boolean validateNotBefore(Date today, Date notBefore) {
        return today.after(notBefore);
    }

    private long validateNotAfter(Date today, Date notAfter) {
        return notAfter.getTime() - today.getTime();
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
}
package com.czertainly.core.service.handler;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.messaging.producers.ValidationProducer;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ComplianceService;
import com.czertainly.core.service.CryptographicKeyService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.LdapUtils;
import com.czertainly.core.util.OcspUtil;
import com.czertainly.core.util.X509ObjectToString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class ValidationHandler {

    private static final Logger logger = LoggerFactory.getLogger(ValidationHandler.class);

    private AttributeEngine attributeEngine;
    private ValidationProducer validationProducer;

    private ComplianceService complianceService;
    private CertificateService certificateService;
    private CertificateEventHistoryService certificateEventHistoryService;
    private CryptographicKeyService cryptographicKeyService;

    private CertificateRepository certificateRepository;
    private DiscoveryRepository discoveryRepository;
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    private PlatformTransactionManager transactionManager;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setValidationProducer(ValidationProducer validationProducer) {
        this.validationProducer = validationProducer;
    }

    @Autowired
    public void setComplianceService(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setCertificateEventHistoryService(CertificateEventHistoryService certificateEventHistoryService) {
        this.certificateEventHistoryService = certificateEventHistoryService;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setDiscoveryRepository(DiscoveryRepository discoveryRepository) {
        this.discoveryRepository = discoveryRepository;
    }

    @Autowired
    public void setDiscoveryCertificateRepository(DiscoveryCertificateRepository discoveryCertificateRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
    }

    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

//    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
//    public void loadValidationContext(Certificate certificate) {
//        certificateService.loadValidationContext(certificate);
//    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<Certificate> getCertificateChain(Certificate subjectCertificate, boolean withEndCertificate) {
        Certificate lastCertificate = subjectCertificate;
        List<Certificate> certificateChain = new ArrayList<>();
        if (withEndCertificate) {
            certificateChain.add(subjectCertificate);
        }

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(true);
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            // Go up the certificate chain until certificate without issuer is found
            while (lastCertificate.getCertificateContent() != null && !lastCertificate.getSubjectType().isSelfSigned()) {
                // Try to find linked issuer certificate in inventory
                if (lastCertificate.getIssuerCertificateUuid() != null) {
                    Certificate issuerCertificate = certificateRepository.findByUuid(lastCertificate.getIssuerCertificateUuid()).orElse(null);
                    if (issuerCertificate != null) {
                        lastCertificate = issuerCertificate;
                        certificateChain.add(issuerCertificate);
                        continue;
                    }
                }
                // Try to find not yet linked issuer certificate in repository
                X509Certificate issuerCert;
                X509Certificate subjectCert;
                boolean issuerInInventory = false;
                try {
                    subjectCert = CertificateUtil.getX509Certificate(lastCertificate.getCertificateContent().getContent());
                } catch (Exception e) {
                    // We do not need to handle exceptions here because if subject certificate cannot be parsed, we cannot update its certificate chain
                    return certificateChain;
                }
                for (Certificate issuer : certificateRepository.findBySubjectDnNormalized(lastCertificate.getIssuerDnNormalized())) {
                    try {
                        issuerCert = CertificateUtil.getX509Certificate(issuer.getCertificateContent().getContent());
                    } catch (Exception e) {
                        // We do not need to handle exceptions here because if certificate cannot be parsed, we ignore it as a
                        // candidate for issuer and continue with next candidate
                        continue;
                    }
                    // Verify signature for a certificate with matching Subject DN, if it matches, the issuer is found
                    if (verifySignature(subjectCert, issuerCert)) {
                        issuerInInventory = true;
                        lastCertificate = issuer;
                        certificateChain.add(issuer);
                        break;
                    }
                }

                // If the issuer isn't in inventory, try to download it from AIA extension of the certificate
                if (!issuerInInventory) {
                    int downloadedCertificates = 0;
                    List<String> aiaChain = downloadChainFromAia(lastCertificate);
                    for (String chainCertificate : aiaChain) {
                        Certificate nextInChain;
                        try {
                            // insert certificate atomically with resolving fingerprint unique conflict and then update issuer uuid and serial number
                            nextInChain = prepareNewChainCertificate(chainCertificate, false);

                            assert nextInChain != null;
                            lastCertificate = nextInChain;
                            certificateChain.add(nextInChain);
                            ++downloadedCertificates;
                        } catch (NoSuchAlgorithmException | CertificateException | NotFoundException e) {
                            // Certificate downloaded from AIA cannot be parsed and inserted into inventory, so ignore the rest of chain
                            break;
                        }
                    }

                    // if downloaded some certificate, try to update chain of last one, if it is really last self-signed
                    if (downloadedCertificates > 0) {
                        logger.debug("Downloaded {} certificates from AIA extension", downloadedCertificates);
                    }
                }
            }
        } finally {
            // it is readonly transaction, so no need to rollback. Commit will just close the transaction and release connection
            transactionManager.commit(status);
        }

        if (!certificateChain.isEmpty()) {
            try {
                updateCertificateChain(certificateChain);
            } catch (Exception e) {
                logger.warn("Unable to update certificate chain issuer information: {}", e.getMessage());
            }
        }

        return certificateChain;
    }

    private void updateCertificateChain(List<Certificate> certificateChain) {
        Certificate issuer ==null;
        for (int i = certificateChain.size() - 1; i >= 0; i--) {
            Certificate certificate = certificateChain.get(i);

            // not saved certificate
            if (certificate.getUuid() == null) {
                // save the certificate content first
                certificateContentRepository.insertWithFingerprintConflictResolve(certificate.getCertificateContent().getFingerprint(), certificate.getCertificateContent().getContent());
                CertificateContent certificateContent = certificateContentRepository.findByFingerprint(certificate.getCertificateContent().getFingerprint());

                OffsetDateTime now = OffsetDateTime.now();
                certificate.setUuid(UUID.randomUUID());
                certificate.setCreated(now);
                certificate.setUpdated(now);
                certificate.setCertificateContent(certificateContent);

                if (issuer != null) {
                    certificate.setIssuerCertificateUuid(issuer.getUuid());
                    certificate.setIssuerSerialNumber(issuer.getSerialNumber());
                }

                byte[] altPublicKey = x509Cert.getExtensionValue(Extension.subjectAltPublicKeyInfo.getId());
                uploadCertificateKey(x509Cert.getPublicKey(), certificateEntity, altPublicKey);
            }
        }
    }

    private Certificate prepareNewChainCertificate(String certificate, boolean assignOwner) throws CertificateException, NoSuchAlgorithmException, NotFoundException {
        X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate);
        String fingerprint = CertificateUtil.getThumbprint(x509Cert);


        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setFingerprint(fingerprint);
        certificateContent.setContent(CertificateUtil.normalizeCertificateContent(X509ObjectToString.toPem(x509Cert)));

        Certificate certificateEntity = new Certificate();
//
        certificateEntity.setFingerprint(fingerprint);
        certificateEntity.setCertificateContent(certificateContent);
        CertificateUtil.prepareIssuedCertificate(certificateEntity, x509Cert);

//        Integer countInserted = certificateRepository.insertWithFingerprintConflictResolve(certificateEntity);
//        certificateEntity = certificateRepository.findByFingerprint(fingerprint).orElseThrow(() -> new NotFoundException(Certificate.class, fingerprint));

        // certificate was actually inserted
        if (countInserted == 1) {
            byte[] altPublicKey = x509Cert.getExtensionValue(Extension.subjectAltPublicKeyInfo.getId());
            uploadCertificateKey(x509Cert.getPublicKey(), certificateEntity, altPublicKey);

            // set owner of certificateEntity to logged user
            if (assignOwner) {
                objectAssociationService.setOwnerFromProfile(Resource.CERTIFICATE, certificateEntity.getUuid());
            }
            certificateComplianceCheck(certificateEntity);
        }
        return certificateEntity;
    }

    private List<String> downloadChainFromAia(Certificate certificate) {
        List<String> chainCertificates = new ArrayList<>();
        String chainUrl;
        try {
            X509Certificate certX509 = CertificateUtil.getX509Certificate(certificate.getCertificateContent().getContent());
            while (true) {
                chainUrl = OcspUtil.getChainFromAia(certX509);
                if (chainUrl == null || chainUrl.isEmpty()) {
                    break;
                }
                String chainContent = downloadChain(chainUrl);
                if (chainContent.isEmpty()) {
                    break;
                }
                logger.info("Certificate {} downloaded from Authority Information Access extension URL {}", certX509.getSubjectX500Principal().getName(), chainUrl);

                chainCertificates.add(chainContent);
                certX509 = CertificateUtil.parseCertificate(chainContent);

                // if self-signed, do not attempt to download itself
                if (verifySignature(certX509, certX509)) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("Unable to get the chain of certificate {} from Authority Information Access", certificate.getUuid(), e);
        }
        return chainCertificates;
    }

    private String downloadChain(String chainUrl) {
        try {
            CertificateFactory fac = CertificateFactory.getInstance("X509");
            X509Certificate cert;
            // Handle ldap protocol

            if (chainUrl.startsWith("ldap://") || chainUrl.startsWith("ldaps://")) {
                byte[] certificate = LdapUtils.downloadFromLdap(chainUrl);
                if (certificate == null) return "";
                cert = (X509Certificate) fac.generateCertificate(new ByteArrayInputStream(certificate));
            } else {
                URL url = URI.create(chainUrl).toURL();
                URLConnection urlConnection = url.openConnection();
                urlConnection.setConnectTimeout(1000);
                urlConnection.setReadTimeout(1000);
                try (InputStream in = url.openStream()) {
                    cert = (X509Certificate) fac.generateCertificate(in);
                } catch (Exception e) {
                    logger.error(e.getMessage());
                    return "";
                }
            }
            final StringWriter writer = new StringWriter();
            final JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
            pemWriter.writeObject(cert);
            pemWriter.flush();
            pemWriter.close();
            writer.close();

            return writer.toString();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return "";
    }

//    private List<Certificate> prepareCertificateChainUpdate(Certificate certificate, List<Certificate> certificateChain) throws CertificateException {
//        while (certificate.getCertificateContent() != null && !certificate.getSubjectType().isSelfSigned()) {
//
//        }
//
//        if (certificate.getCertificateContent() == null || certificate.getSubjectType().isSelfSigned()) {
//            return certificateChain;
//        }
//
//        boolean issuerInInventory = false;
//        X509Certificate subCert;
//        try {
//            subCert = CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent());
//        } catch (Exception e) {
//            // We do not need to handle exceptions here because if subject certificate cannot be parsed, we cannot update its certificate chain
//            return certificateChain;
//        }
//        // Try to find issuer certificate in repository
//        for (Certificate issuer : certificateRepository.findBySubjectDnNormalized(certificate.getIssuerDnNormalized())) {
//            X509Certificate issCert;
//            try {
//                issCert = CertificateUtil.parseCertificate(issuer.getCertificateContent().getContent());
//            } catch (Exception e) {
//                // We do not need to handle exceptions here because if certificate cannot be parsed, we ignore it as a
//                // candidate for issuer and continue with next candidate
//                continue;
//            }
//            // Verify signature for a certificate with matching Subject DN, if it matches, the issuer is found
//            if (verifySignature(subCert, issCert)) {
//                issuerInInventory = true;
//                certificate.setIssuerSerialNumber(issuer.getSerialNumber());
//                certificate.setIssuerCertificateUuid(issuer.getUuid());
//                certificateRepository.save(certificate);
//                // If the issuer of certificate doesn't have its issuer, try to update issuer for this certificate as well
//                if (issuer.getIssuerCertificateUuid() == null) {
//                    prepareCertificateChainUpdate(issuer, certificateChain);
//                }
//                break;
//            }
//        }
//        // If the issuer isn't in inventory, try to download it from AIA extension of the certificate
//        if (!issuerInInventory) {
//            int downloadedCertificates = 0;
//            List<String> aiaChain = downloadChainFromAia(certificate);
//            Certificate previousCertificate = certificate;
//            for (String chainCertificate : aiaChain) {
//                Certificate nextInChain;
//                try {
//                    // insert certificate atomically with resolvibg fingerprint unique conflict and then update issuer uuid and serial number
//                    nextInChain = createCertificateAtomic(chainCertificate, false);
//
//                    assert nextInChain != null;
//                    previousCertificate.setIssuerCertificateUuid(nextInChain.getUuid());
//                    previousCertificate.setIssuerSerialNumber(nextInChain.getSerialNumber());
//                    previousCertificate = nextInChain;
//                    ++downloadedCertificates;
//                } catch (NoSuchAlgorithmException | CertificateException | NotFoundException e) {
//                    // Certificate downloaded from AIA cannot be parsed and inserted into inventory, so ignore the rest of chain
//                    break;
//                }
//            }
//
//            // if downloaded some certificate, try to update chain of last one, if it is really last self-signed
//            if (downloadedCertificates > 0) {
//                updateCertificateChain(previousCertificate);
//            }
//        }
//    }

    private boolean verifySignature(X509Certificate subjectCertificate, X509Certificate issuerCertificate) {
        try {
            subjectCertificate.verify(issuerCertificate.getPublicKey());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Certificate constructCertificateChainFromInventory(Certificate certificate, List<Certificate> certificateChain) {
        Certificate lastCertificate = certificate;
        // Go up the certificate chain until certificate without issuer is found
        while (lastCertificate.getIssuerCertificateUuid() != null) {
            Certificate issuerCertificate = certificateRepository.findByUuid(lastCertificate.getIssuerCertificateUuid()).orElse(null);
            if (issuerCertificate != null) {
                certificateChain.add(issuerCertificate);
                lastCertificate = issuerCertificate;
            } else {
                // move updating issuer certificate UUID and serial number to the delete method of certificate
//                lastCertificate.setIssuerCertificateUuid(null);
//                lastCertificate.setIssuerSerialNumber(null);
//                certificateRepository.save(lastCertificate);
            }
        }

        return lastCertificate;
    }
}

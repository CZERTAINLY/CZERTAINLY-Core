package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.authority.CertificateRevocationReason;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.CrlEntryRepository;
import com.czertainly.core.dao.repository.CrlRepository;
import com.czertainly.core.service.CrlService;
import com.czertainly.core.util.CrlUtil;
import com.czertainly.core.util.CzertainlyX500NameStyle;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.cert.*;
import java.util.*;

@Service
public class CrlServiceImpl implements CrlService {

    private static final Logger logger = LoggerFactory.getLogger(CrlServiceImpl.class);

    private CertificateRepository certificateRepository;

    private CrlRepository crlRepository;

    private CrlEntryRepository crlEntryRepository;

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setCrlRepository(CrlRepository crlRepository) {
        this.crlRepository = crlRepository;
    }

    @Autowired
    public void setCrlEntryRepository(CrlEntryRepository crlEntryRepository) {
        this.crlEntryRepository = crlEntryRepository;
    }

    @Override
    public Crl getCurrentCrl(X509Certificate certificate, X509Certificate issuerCertificate) throws IOException {
        byte[] issuerDnPrincipalEncoded = certificate.getIssuerX500Principal().getEncoded();
        String issuerDn = X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, issuerDnPrincipalEncoded).toString();
        String issuerSerialNumber = issuerCertificate.getSerialNumber().toString(16);
        Crl crl = crlRepository.findByIssuerDnAndSerialNumber(issuerDn, issuerSerialNumber).orElse(null);
        Certificate caCertificate = certificateRepository.findBySubjectDnNormalizedAndSerialNumber(issuerDn, issuerSerialNumber).orElse(null);
        byte[] crlDistributionPoints = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());

        UUID caCertificateUuid = caCertificate != null ? caCertificate.getUuid() : null;
        // If CRL is not present or current UTC time is past its next_update timestamp, download the CRL and save the CRL and its entries in database
        if (crl == null || crl.getNextUpdate().before(new Date())) {
            Crl newCrl = createCrlAndCrlEntries(crlDistributionPoints, issuerDn, issuerSerialNumber, caCertificateUuid, crl);
            // If CRL received is not null, then the downloaded CRL is updated CRL, delete old CRL and use updated one
            if (newCrl != null) {
                crl = newCrl;
            }
        }

        // Check if certificate has freshestCrl extension set
        if (certificate.getExtensionValue(Extension.freshestCRL.getId()) != null) {
            // If no delta CRL is set or delta CRL is not up-to-date, download delta CRL
            if (crl != null && (crl.getNextUpdateDelta() == null || !crl.getNextUpdateDelta().before(new Date()))) {
                updateCrlAndCrlEntriesFromDeltaCrl(certificate, crl, issuerDn, issuerSerialNumber, caCertificateUuid);
            }
        }
        return crl;
    }

    @Override
    public CrlEntry findCrlEntryForCertificate(String serialNumber, UUID crlUuid) {
        CrlEntryId crlEntryId = new CrlEntryId(crlUuid, serialNumber);
        return crlEntryRepository.findById(crlEntryId).orElse(null);
    }

    @Override
    public List<Crl> findCrlsForCaCertificate(UUID caCertificateUuid) {
        return crlRepository.findByCaCertificateUuid(caCertificateUuid);
    }

    private Crl createCrlAndCrlEntries(byte[] crlDistributionPointsEncoded, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid, Crl oldCrl) throws IOException {
        List<String> crlUrls = CrlUtil.getCDPFromCertificate(crlDistributionPointsEncoded);

        Crl crl = null;

        for (String crlUrl : crlUrls) {
            X509CRL X509Crl;
            try {
                X509Crl = CrlUtil.getX509Crl(crlUrl);
            } catch (Exception e) {
                // Failed to read content from URL, continue to next URL
                logger.error("Failed to read CRL content from URL: {}, {}", crlUrl, e.getMessage());
                continue;
            }

            String crlNumber = JcaX509ExtensionUtils.parseExtensionValue(X509Crl.getExtensionValue(Extension.cRLNumber.getId())).toString();

            boolean isNewCrl = oldCrl == null;
            if (!isNewCrl) {
                if (Objects.equals(crlNumber, oldCrl.getCrlNumber())) return null;
                crl = oldCrl;
                crlEntryRepository.deleteAllByCrlUuid(oldCrl.getUuid());
            } else {
                crl = new Crl();
                crl.setUuid(UUID.randomUUID());
                byte[] issuerDnPrincipalEncoded = X509Crl.getIssuerX500Principal().getEncoded();
                crl.setCrlIssuerDn(X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, issuerDnPrincipalEncoded).toString());
                crl.setSerialNumber(issuerSerialNumber);
                crl.setIssuerDn(issuerDn);
                crl.setCaCertificateUuid(caCertificateUuid);
            }

            crl.setNextUpdate(X509Crl.getNextUpdate());
            crl.setCrlNumber(crlNumber);
            List<CrlEntry> crlEntries = new ArrayList<>();
            crl.setCrlEntries(crlEntries);

            if (isNewCrl) {
                crlRepository.insertWithIssuerConflictResolve(crl);
                crl = crlRepository.findByIssuerDnAndSerialNumber(issuerDn, issuerSerialNumber).orElse(null);
                if (crl == null) return crl;
            } else {
                crlRepository.save(crl);
            }

            Set<? extends X509CRLEntry> crlCertificates = X509Crl.getRevokedCertificates();
            if (crlCertificates != null) {
                Date lastRevocationDate = new Date(0);
                for (X509CRLEntry x509CRLEntry : crlCertificates) {
                    CrlEntry crlEntry = createCrlEntry(x509CRLEntry, crl);
                    crlEntries.add(crlEntry);
                    if (crlEntry.getRevocationDate().after(lastRevocationDate))
                        lastRevocationDate = crlEntry.getRevocationDate();
                }
                crl.setLastRevocationDate(lastRevocationDate);
                crlRepository.save(crl);
            }

            // Managed to process a CRL url and do not need to try other URLs
            break;
        }
        return crl;
    }

    private void updateCrlAndCrlEntriesFromDeltaCrl(X509Certificate certificate, Crl crl, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid) throws IOException {
        List<String> deltaCrlUrls = CrlUtil.getCDPFromCertificate(certificate.getExtensionValue(Extension.freshestCRL.getId()));
        for (String deltaCrlUrl : deltaCrlUrls) {
            X509CRL deltaCrl;
            try {
                deltaCrl = CrlUtil.getX509Crl(deltaCrlUrl);
            } catch (Exception e) {
                // Failed to read content from URL, continue to next URL
                continue;
            }
            String deltaCrlIssuer = X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, deltaCrl.getIssuerX500Principal().getEncoded()).toString();
            // Compare CRL issuer with issuer stored in CRL entity, delta CRL is invalid if they are not the same
            if (!Objects.equals(deltaCrlIssuer, crl.getCrlIssuerDn()))
                throw new ValidationException("Delta CRL issuer not same as issuer stored in CRL entity");

            // Compare DeltaCRLIndicator with base CRL number, if they are not equal, try to get newer CRL
            String deltaCrlIndicator = JcaX509ExtensionUtils.parseExtensionValue(deltaCrl.getExtensionValue(Extension.deltaCRLIndicator.getId())).toString();
            if (!Objects.equals(deltaCrlIndicator, crl.getCrlNumber())) {
                Crl newCrl = createCrlAndCrlEntries(certificate.getExtensionValue(Extension.cRLDistributionPoints.getId()), issuerDn, issuerSerialNumber, caCertificateUuid, crl);
                // If received CRL is null, it means it is the old one again, and we are not able to set delta CRL properly
                if (newCrl == null)
                    throw new ValidationException("Unable to get CRL with base CRL number equal to DeltaCRLIndicator");
                // Otherwise delete the old CRL and continue with the new CRL
                crl = newCrl;
            }
            updateDeltaCrl(crl, deltaCrl);
            // Managed to process a delta CRL url and do not need to try other URLs
            break;
        }
    }


    private void updateDeltaCrl(Crl crl, X509CRL deltaCrl) throws IOException {
        ASN1Primitive encodedCrlNumber = JcaX509ExtensionUtils.parseExtensionValue(deltaCrl.getExtensionValue(Extension.cRLNumber.getId()));
        // If delta CRL number has been set, check if delta CRL number is greater than one in DB entity, if it is, process delta CRL entries
        if (crl.getCrlNumberDelta() == null || Integer.parseInt(encodedCrlNumber.toString()) > Integer.parseInt(crl.getCrlNumberDelta())) {
            List<CrlEntry> crlEntries = crl.getCrlEntries();
            Date lastRevocationDateNew = crl.getLastRevocationDate();
            Set<? extends X509CRLEntry> deltaCrlEntries = deltaCrl.getRevokedCertificates();
            if (deltaCrlEntries != null) {
                Map<String, CrlEntry> crlEntryMap = crl.getCrlEntriesMap();
                for (X509CRLEntry deltaCrlEntry : deltaCrlEntries) {
                    Date entryRevocationDate = deltaCrlEntry.getRevocationDate();
                    // Process only entries which revocation date is >= last_revocation_date, others are already in DB
                    if (entryRevocationDate.after(crl.getLastRevocationDate()) || entryRevocationDate.equals(crl.getLastRevocationDate())) {
                        String serialNumber = String.valueOf(deltaCrlEntry.getSerialNumber());
                        CrlEntry crlEntry = crlEntryMap.get(serialNumber);
                        //  Entry by serial number is not present, add new one
                        if (crlEntry == null) {
                            CrlEntry crlEntryNew = createCrlEntry(deltaCrlEntry, crl);
                            crlEntries.add(crlEntryNew);
                            // Entry by serial number is present and revocation reason is REMOVE_FROM_CRL, remove this entry
                        } else if (Objects.equals(deltaCrlEntry.getRevocationReason(), CRLReason.REMOVE_FROM_CRL)) {
                            crlEntries.remove(crlEntry);
                            crlEntryRepository.delete(crlEntry);
                            // Entry by serial number is present, probably reason changed so update its revocation reason and date
                        } else {
                            crlEntry.setRevocationReason(deltaCrlEntry.getRevocationReason() == null ? CertificateRevocationReason.UNSPECIFIED : CertificateRevocationReason.fromCrlReason(deltaCrlEntry.getRevocationReason()));
                            crlEntry.setRevocationDate(deltaCrlEntry.getRevocationDate());
                            crlEntryRepository.save(crlEntry);
                        }
                        if (lastRevocationDateNew.before(deltaCrlEntry.getRevocationDate()))
                            lastRevocationDateNew = deltaCrlEntry.getRevocationDate();
                    }
                }
            }
            // Update last revocation date from new/updated entries
            crl.setLastRevocationDate(lastRevocationDateNew);
            crl.setCrlNumberDelta(encodedCrlNumber.toString());
            crl.setNextUpdateDelta(deltaCrl.getNextUpdate());
            crlRepository.save(crl);
        }
    }

    private CrlEntry createCrlEntry(X509CRLEntry x509CRLEntry, Crl crl) {
        String serialNumber = x509CRLEntry.getSerialNumber().toString(16);
        crlEntryRepository.insertWithIdConflictResolve(crl.getUuid(), serialNumber, x509CRLEntry.getRevocationDate(),
                x509CRLEntry.getRevocationReason() == null ? CertificateRevocationReason.UNSPECIFIED.name() : CertificateRevocationReason.fromCrlReason(x509CRLEntry.getRevocationReason()).name());

        return findCrlEntryForCertificate(serialNumber, crl.getUuid());
    }

}

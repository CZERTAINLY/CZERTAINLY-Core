package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.CertificateValidationCheck;
import com.czertainly.api.model.core.certificate.CertificateValidationCheckDto;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.CrlEntryRepository;
import com.czertainly.core.dao.repository.CrlRepository;
import com.czertainly.core.service.CrlService;
import com.czertainly.core.util.CrlUtil;
import com.czertainly.core.util.CzertainlyX500NameStyle;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.security.cert.*;
import java.util.*;

public class CrlServiceImpl implements CrlService {
    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CrlRepository crlRepository;

    @Autowired
    private CrlEntryRepository crlEntryRepository;

    @Override
    public Crl createCrlAndCrlEntries(byte[] crlDistributionPointsEncoded, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid) throws IOException {
        // TODO zredukovat vstup
        List<String> crlUrls = CrlUtil.getCDPFromCertificate(crlDistributionPointsEncoded);

        Crl crl = null;

        for (String crlUrl : crlUrls) {
            crl = new Crl();
            crl.setSerialNumber(issuerSerialNumber);
            crl.setIssuerDn(issuerDn);
            crl.setCaCertificateUuid(caCertificateUuid);
            List<CrlEntry> crlEntries;
            try {
                crlEntries = prepareCrlAndCrlEntries(crlUrl, crl);
            } catch (CertificateException | CRLException e) {
                // Failed to read content from URL, continue to next URL
                continue;
            }
            crl.setCrlEntries(crlEntries);
            crl.setLastRevocationDate(Collections.max(crlEntries, Comparator.comparing(CrlEntry::getRevocationDate)).getRevocationDate());
            crlRepository.save(crl);

            crlEntryRepository.saveAll(crlEntries);

            for (CrlEntry crlEntry : crlEntries) {
                crlEntry.setCrlUuid(crl.getUuid());
            }
            // Managed to process a CRL url and do not need to try other URLs
            break;
        }
        return crl;
    }

    @Override
    public Crl createDeltaCrlAndCrlEntries(X509Certificate certificate, Crl crl, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid) throws IOException {
        List<String> deltaCrlUrls = CrlUtil.getCDPFromCertificate(certificate.getExtensionValue(Extension.freshestCRL.getId()));
        for (String deltaCrlUrl: deltaCrlUrls) {
            try {
                updateDeltaCrl(crl, deltaCrlUrl);
            } catch (CertificateException e) {
                // Failed to read content from URL, continue to next URL
                continue;
            } catch (ValidationException e) {
                // Downloaded Delta CRL is invalid, CRL issuer is not same as the issuer stored in CRL entity
                if (e.getMessage().contains("issuer")) throw e;
                // DeltaCRLIndicator base CRL number is not equal to one from CRL entity, redownload full CRL
                Crl newCrl = createCrlAndCrlEntries(certificate.getExtensionValue(Extension.cRLDistributionPoints.getId()), issuerDn, issuerSerialNumber, caCertificateUuid);
                // Received again old CRL, failed to retrieve valid CRL
                if (Objects.equals(crl.getCrlNumber(), newCrl.getCrlNumber())) throw e;
                // Update delta CRL of the new CRL instead
                crl = newCrl;
                try {
                    updateDeltaCrl(crl, deltaCrlUrl);
                } catch (CertificateException ex) {
                    continue;
                }
            }
            break;
        }
        return crl;
    }


    @Override
    public Crl getCurrentCrl(X509Certificate certificate, X509Certificate issuerCertificate) throws IOException {
        Crl crl;
        byte[] issuerDnPrincipalEncoded = certificate.getIssuerX500Principal().getEncoded();
        String issuerDn = X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, issuerDnPrincipalEncoded).toString();
        String issuerSerialNumber = issuerCertificate.getSerialNumber().toString();
        Optional<Crl> crlOptional = crlRepository.findByIssuerDnAndSerialNumber(issuerDn, issuerSerialNumber);
        UUID caCertificateUuid = certificateRepository.findByIssuerDnNormalizedAndSerialNumber(issuerDn, issuerSerialNumber).get().getUuid();
        byte[] crlDistributionPoints = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());
        // If CRL is present, but current UTC time is past its next_update timestamp, download the CRL and save the CRL and its entries in database
        if (crlOptional.isPresent() && crlOptional.get().getNextUpdate().before(new Date())) {
            crlRepository.delete(crlOptional.get());
            crl = createCrlAndCrlEntries(crlDistributionPoints, issuerDn, issuerSerialNumber, caCertificateUuid);
        }
        else if (crlOptional.isPresent()) {
            // Get from database if CRL is present
            crl = crlOptional.get();
        } else {
            // If CRL is not present, download the CRL and save the CRL and its entries in database
            crl = createCrlAndCrlEntries(crlDistributionPoints, issuerDn, issuerSerialNumber, caCertificateUuid);
        }

        // Check if certificate has freshestCrl extension set
        if (certificate.getExtensionValue(Extension.freshestCRL.getId()) != null) {
            // If no delta CRL is set or delta CRL is not up-to-date, download delta CRL
            if (crl.getNextUpdateDelta() == null || !crl.getNextUpdateDelta().before(new Date())) {
                createDeltaCrlAndCrlEntries(certificate, crl, issuerDn, issuerSerialNumber, caCertificateUuid);
            }
        }
        return crl;
    }

    private void updateDeltaCrl(Crl crl, String deltaCrlUrl) throws IOException, CertificateException {
        X509CRL deltaCrl = CrlUtil.getX509Crl(deltaCrlUrl);
        int oldDeltaCrl = Integer.parseInt(crl.getCrlNumberDelta());
        String deltaCrlIssuer = X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, deltaCrl.getIssuerX500Principal().getEncoded()).toString();
        // Compare CRL issuer with issuer stored in CRL entity, delta CRL is invalid if they are not the same
        if (!Objects.equals(deltaCrlIssuer, crl.getCrlIssuerDn()))
            throw new ValidationException("Delta CRL issuer not same as issuer stored in CRL entity");

        // Compare DeltaCRLIndicator with base CRL number, if they are not equal, delta CRL is invalid
        byte[] deltaCrlIndicatorEncoded = deltaCrl.getExtensionValue(Extension.deltaCRLIndicator.getId());
        if (!Objects.equals(JcaX509ExtensionUtils.parseExtensionValue(deltaCrlIndicatorEncoded).toString(), crl.getCrlNumber()))
            throw new ValidationException("DeltaCRLIndicator base CRL number is not equal to one from CRL entity");

        byte[] encodedCrlNumber = deltaCrl.getExtensionValue(Extension.cRLNumber.getId());
        crl.setCrlNumberDelta(JcaX509ExtensionUtils.parseExtensionValue(encodedCrlNumber).toString());
        // Check if delta CRL number is greater than one in DB entity, if it is, process delta CRL entries
        if (Integer.parseInt(crl.getCrlNumber()) > oldDeltaCrl) {
            List<CrlEntry> crlEntries = crl.getCrlEntries();
            Date lastRevocationDateNew = crl.getLastRevocationDate();
            Set<? extends X509CRLEntry> deltaCrlEntries = deltaCrl.getRevokedCertificates();
            for (X509CRLEntry deltaCrlEntry: deltaCrlEntries) {
                Date entryRevocationDate = deltaCrlEntry.getRevocationDate();
                // Process only entries which revocation date is >= last_revocation_date, others are already in DB
                if (entryRevocationDate.after(lastRevocationDateNew)) {
                    String serialNumber = String.valueOf(deltaCrlEntry.getSerialNumber());
                    CrlEntryId id = new CrlEntryId(crl.getUuid(), serialNumber);
                    Optional<CrlEntry> crlEntry = crlEntryRepository.findById(id);
                    //  Entry by serial number is not present, add new one
                    if (crlEntry.isEmpty()) {
                        CrlEntry crlEntryNew = createCrlEntry(deltaCrlEntry, crl.getUuid());
                        crlEntryNew.setCrlUuid(crl.getUuid());
                        crlEntries.add(crlEntryNew);
                        if (lastRevocationDateNew.before(deltaCrlEntry.getRevocationDate()))
                            lastRevocationDateNew = deltaCrlEntry.getRevocationDate();
                        // Entry by serial number is present and revocation reason is REMOVE_FROM_CRL, remove this entry
                    } else if (Objects.equals(deltaCrlEntry.getRevocationReason().toString(), "REMOVE_FROM_CRL")) {
                        crlEntries.remove(crlEntry);
                        crlEntryRepository.delete(crlEntry.get());
                        // Entry by serial number is present, probably reason changed so update its revocation reason and date
                    } else {
                        crlEntry.get().setRevocationReason(deltaCrlEntry.getRevocationReason().toString());
                        crlEntry.get().setRevocationDate(deltaCrlEntry.getRevocationDate());
                        if (lastRevocationDateNew.before(deltaCrlEntry.getRevocationDate()))
                            lastRevocationDateNew = deltaCrlEntry.getRevocationDate();
                    }
                }
            }
            // Update last revocation date from new/updated entries
            crl.setLastRevocationDate(lastRevocationDateNew);
        }
    }

    private List<CrlEntry> prepareCrlAndCrlEntries(String crlUrl, Crl modal) throws IOException, CertificateException, CRLException {
        X509CRL X509Crl = CrlUtil.getX509Crl(crlUrl);
        modal.setNextUpdate(X509Crl.getNextUpdate());
        byte[] issuerDnPrincipalEncoded = X509Crl.getIssuerX500Principal().getEncoded();
        modal.setCrlIssuerDn(X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, issuerDnPrincipalEncoded).toString());
        modal.setCrlNumber(JcaX509ExtensionUtils.parseExtensionValue(X509Crl.getExtensionValue(Extension.cRLNumber.getId())).toString());

        return getCrlEntries(X509Crl);
    }

    private List<CrlEntry> getCrlEntries(X509CRL x509CRL) {
        Set<? extends X509CRLEntry> crlCertificates = x509CRL.getRevokedCertificates();
        List<CrlEntry> crlEntries = new ArrayList<>();
        for (X509CRLEntry x509CRLEntry : crlCertificates) {
            CrlEntry crlEntry = new CrlEntry();
            crlEntry.setRevocationDate(x509CRLEntry.getRevocationDate());
            crlEntry.setSerialNumber(x509CRLEntry.getSerialNumber().toString());
            crlEntry.setRevocationReason(x509CRLEntry.getRevocationReason().toString());
            crlEntries.add(crlEntry);
        }
        return crlEntries;
    }

    private CrlEntry createCrlEntry(X509CRLEntry x509CRLEntry, UUID crlUuid) {
        CrlEntry crlEntry = new CrlEntry();
        crlEntry.setRevocationDate(x509CRLEntry.getRevocationDate());
        crlEntry.setSerialNumber(x509CRLEntry.getSerialNumber().toString());
        if (x509CRLEntry.getRevocationReason() != null) {
            crlEntry.setRevocationReason(x509CRLEntry.getRevocationReason().toString());
        } else {
            crlEntry.setRevocationReason("UNKNOWN");
        }
        crlEntry.setCrlUuid(crlUuid);
        crlEntryRepository.save(crlEntry);
        return crlEntry;
    }
}

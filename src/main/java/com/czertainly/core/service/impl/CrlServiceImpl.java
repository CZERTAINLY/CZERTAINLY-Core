package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.core.dao.entity.Crl;
import com.czertainly.core.dao.entity.CrlEntry;
import com.czertainly.core.dao.entity.CrlEntryId;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.cert.*;
import java.util.*;

@Service
public class CrlServiceImpl implements CrlService {

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
    public Crl createCrlAndCrlEntries(byte[] crlDistributionPointsEncoded, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid, String oldCrlNumber) throws IOException {
        List<String> crlUrls = CrlUtil.getCDPFromCertificate(crlDistributionPointsEncoded);

        Crl crl = null;

        for (String crlUrl : crlUrls) {
            crl = new Crl();
            crl.setSerialNumber(issuerSerialNumber);
            crl.setIssuerDn(issuerDn);
            crl.setCaCertificateUuid(caCertificateUuid);
            List<CrlEntry> crlEntries = new ArrayList<>();
            X509CRL X509Crl;
            try {
                X509Crl = CrlUtil.getX509Crl(crlUrl);
            } catch (CertificateException e) {
                // Failed to read content from URL, continue to next URL
                continue;
            }
            crl.setNextUpdate(X509Crl.getNextUpdate());
            byte[] issuerDnPrincipalEncoded = X509Crl.getIssuerX500Principal().getEncoded();
            crl.setCrlIssuerDn(X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, issuerDnPrincipalEncoded).toString());
            String crlNumber = JcaX509ExtensionUtils.parseExtensionValue(X509Crl.getExtensionValue(Extension.cRLNumber.getId())).toString();
            if (Objects.equals(crlNumber, oldCrlNumber)) return null;
            crl.setCrlNumber(crlNumber);
            crl.setCrlEntries(crlEntries);
            crlRepository.save(crl);

            Set<? extends X509CRLEntry> crlCertificates = X509Crl.getRevokedCertificates();
            for (X509CRLEntry x509CRLEntry : crlCertificates) {
              CrlEntry crlEntry = createCrlEntry(x509CRLEntry, crl);
              crlEntries.add(crlEntry);
            }

            crl.setLastRevocationDate(Collections.max(crlEntries, Comparator.comparing(CrlEntry::getRevocationDate)).getRevocationDate());


            // Managed to process a CRL url and do not need to try other URLs
            break;
        }
        return crl;
    }

    @Override
    public Crl updateCrlAndCrlEntriesFromDeltaCrl(X509Certificate certificate, Crl crl, String issuerDn, String issuerSerialNumber, UUID caCertificateUuid) throws IOException {
        List<String> deltaCrlUrls = CrlUtil.getCDPFromCertificate(certificate.getExtensionValue(Extension.freshestCRL.getId()));
        for (String deltaCrlUrl : deltaCrlUrls) {
            X509CRL deltaCrl;
            try {
                deltaCrl = CrlUtil.getX509Crl(deltaCrlUrl);
            } catch (CertificateException e) {
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
                Crl newCrl = createCrlAndCrlEntries(certificate.getExtensionValue(Extension.cRLDistributionPoints.getId()), issuerDn, issuerSerialNumber, caCertificateUuid, crl.getCrlNumber());
                // If received CRL is null, it means it is the old one again, and we are not able to set delta CRL properly
                if (newCrl == null) throw new ValidationException("Unable to get CRL with base CRL number equal to DeltaCRLIndicator");
                // Otherwise delete the old CRL and continue with the new CRL
                crlRepository.delete(crl);
                crl = newCrl;
            }
            updateDeltaCrl(crl, deltaCrl);
            // Managed to process a delta CRL url and do not need to try other URLs
            break;
        }
        return crl;
    }


    @Override
    public Crl getCurrentCrl(X509Certificate certificate, X509Certificate issuerCertificate) throws IOException {
        Crl crl;
        byte[] issuerDnPrincipalEncoded = certificate.getIssuerX500Principal().getEncoded();
        String issuerDn = X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED, issuerDnPrincipalEncoded).toString();
        String issuerSerialNumber = issuerCertificate.getSerialNumber().toString(16);
        Optional<Crl> crlOptional = crlRepository.findByIssuerDnAndSerialNumber(issuerDn, issuerSerialNumber);
        UUID caCertificateUuid = certificateRepository.findByIssuerDnNormalizedAndSerialNumber(issuerDn, issuerSerialNumber).get().getUuid();
        byte[] crlDistributionPoints = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());
        // If CRL is present, but current UTC time is past its next_update timestamp, download the CRL and save the CRL and its entries in database
        if (crlOptional.isPresent() && crlOptional.get().getNextUpdate().before(new Date())) {
            crl = createCrlAndCrlEntries(crlDistributionPoints, issuerDn, issuerSerialNumber, caCertificateUuid, crlOptional.get().getCrlNumber());
            // If CRL received is null, then the downloaded CRL is the old CRL, so use that one
            if (crl != null) crlRepository.delete(crlOptional.get()); else crl = crlOptional.get();
        } else if (crlOptional.isPresent()) {
            // Get from database if CRL is present
            crl = crlOptional.get();
        } else {
            // If CRL is not present, download the CRL and save the CRL and its entries in database
            crl = createCrlAndCrlEntries(crlDistributionPoints, issuerDn, issuerSerialNumber, caCertificateUuid, "");
        }

        // Check if certificate has freshestCrl extension set
        if (certificate.getExtensionValue(Extension.freshestCRL.getId()) != null) {
            // If no delta CRL is set or delta CRL is not up-to-date, download delta CRL
            if (crl.getNextUpdateDelta() == null || !crl.getNextUpdateDelta().before(new Date())) {
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

    private void updateDeltaCrl(Crl crl, X509CRL deltaCrl) throws IOException {

        ASN1Primitive encodedCrlNumber = JcaX509ExtensionUtils.parseExtensionValue(deltaCrl.getExtensionValue(Extension.cRLNumber.getId()));
        // If delta CRL number has been set, check if delta CRL number is greater than one in DB entity, if it is, process delta CRL entries
        if (crl.getCrlNumberDelta() == null || Integer.parseInt(encodedCrlNumber.toString()) > Integer.parseInt(crl.getCrlNumberDelta())) {
            List<CrlEntry> crlEntries = crl.getCrlEntries();
            Date lastRevocationDateNew = crl.getLastRevocationDate();
            Set<? extends X509CRLEntry> deltaCrlEntries = deltaCrl.getRevokedCertificates();
            if (deltaCrlEntries != null) {
                for (X509CRLEntry deltaCrlEntry : deltaCrlEntries) {
                    Date entryRevocationDate = deltaCrlEntry.getRevocationDate();
                    // Process only entries which revocation date is >= last_revocation_date, others are already in DB
                    if (entryRevocationDate.after(lastRevocationDateNew)) {
                        String serialNumber = String.valueOf(deltaCrlEntry.getSerialNumber());
                        CrlEntryId id = new CrlEntryId(crl.getUuid(), serialNumber);
                        Optional<CrlEntry> crlEntry = crlEntryRepository.findById(id);
                        //  Entry by serial number is not present, add new one
                        if (crlEntry.isEmpty()) {
                            CrlEntry crlEntryNew = createCrlEntry(deltaCrlEntry, crl);
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
            }
            // Update last revocation date from new/updated entries
            crl.setLastRevocationDate(lastRevocationDateNew);
            crl.setCrlNumberDelta(encodedCrlNumber.toString());
            crl.setNextUpdateDelta(deltaCrl.getNextUpdate());
        }
    }


    private CrlEntry createCrlEntry(X509CRLEntry x509CRLEntry, Crl crl) {
        CrlEntry crlEntry = new CrlEntry();
        crlEntry.setCrl(crl);
        crlEntry.getId().setSerialNumber(x509CRLEntry.getSerialNumber().toString(16));
        crlEntry.getId().setCrlUuid(crl.getUuid());
        crlEntry.setRevocationDate(x509CRLEntry.getRevocationDate());
        if (x509CRLEntry.getRevocationReason() != null) {
            crlEntry.setRevocationReason(x509CRLEntry.getRevocationReason().toString());
        } else {
            crlEntry.setRevocationReason("UNKNOWN");
        }
        crlEntry.setCrl(crl);
        crlEntryRepository.save(crlEntry);
        return crlEntry;
    }

}

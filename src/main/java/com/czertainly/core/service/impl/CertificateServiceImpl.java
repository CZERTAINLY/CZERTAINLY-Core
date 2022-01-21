package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerBulkUpdateDto;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.X509ObjectToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR", "ROLE_CLIENT", "ROLE_ACME"})
public class CertificateServiceImpl implements CertificateService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateServiceImpl.class);

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private CertValidationService certValidationService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    public List<CertificateDto> listCertificates(Integer start, Integer end) {
        if (start != null && end != null) {
            if (start > 1) {
                start = start - 1;
            }
            Pageable page = PageRequest.of(start, end + 1);
            return certificateRepository.findAll(page).stream().map(Certificate::mapToDto).collect(Collectors.toList());

        }
        return certificateRepository.findAll().stream().map(Certificate::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    public CertificateDto getCertificate(String uuid) throws NotFoundException {
        return getCertificateEntity(uuid).mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    public Certificate getCertificateEntity(String uuid) throws NotFoundException {
        return certificateRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    public Certificate getCertificateEntityByContent(String content) {
        CertificateContent certificateContent = certificateContentRepository.findByContent(content);
        return certificateRepository.findByCertificateContent(certificateContent);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    public Certificate getCertificateEntityBySerial(String serialNumber) throws NotFoundException {
        return certificateRepository.findBySerialNumberIgnoreCase(serialNumber)
                .orElseThrow(() -> new NotFoundException(Certificate.class, serialNumber));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.DELETE)
    public void removeCertificate(String uuid) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));

        List<ValidationError> errors = new ArrayList<>();

        for (Client client : clientRepository.findByCertificate(certificate)) {
            errors.add(ValidationError.create("Certificate has Client " + client.getName() + " associated to it"));
        }

        for (Admin admin : adminRepository.findByCertificate(certificate)) {
            errors.add(ValidationError.create("Certificate has Admin " + admin.getName() + " associated to it"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete certificate", errors);
        }
        if (discoveryCertificateRepository.findByCertificateContent(certificate.getCertificateContent()).isEmpty()) {
            CertificateContent content = certificateContentRepository
                    .findById(certificate.getCertificateContent().getId()).orElse(null);
            if (content != null) {
                certificateContentRepository.delete(content);
            }
        }

        certificateRepository.delete(certificate);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateRaProfile(String uuid, CertificateUpdateRAProfileDto request) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        RaProfile raProfile = raProfileRepository.findByUuid(request.getRaProfileUuid())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getRaProfileUuid()));
        certificate.setRaProfile(raProfile);
        certificateRepository.save(certificate);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateCertificateGroup(String uuid, CertificateUpdateGroupDto request) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));

        CertificateGroup certificateGroup = groupRepository.findByUuid(request.getGroupUuid())
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, request.getGroupUuid()));

        certificate.setGroup(certificateGroup);
        certificateRepository.save(certificate);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateEntity(String uuid, CertificateUpdateEntityDto request) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        CertificateEntity certificateEntity = entityRepository.findByUuid(request.getEntityUuid())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getEntityUuid()));
        certificate.setEntity(certificateEntity);
        certificateRepository.save(certificate);

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateOwner(String uuid, CertificateOwnerRequestDto request) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        certificate.setOwner(request.getOwner());
        certificateRepository.save(certificate);

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateRaProfile(MultipleRAProfileUpdateDto request) throws NotFoundException {
        for (String certificateUuid : request.getCertificateUuids()) {
            Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                    .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
            RaProfile raProfile = raProfileRepository.findByUuid(request.getUuid())
                    .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getUuid()));
            certificate.setRaProfile(raProfile);
            certificateRepository.save(certificate);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateCertificateGroup(MultipleGroupUpdateDto request) throws NotFoundException {
        for (String certificateUuid : request.getCertificateUuids()) {
            Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                    .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));

            CertificateGroup certificateGroup = groupRepository.findByUuid(request.getUuid())
                    .orElseThrow(() -> new NotFoundException(CertificateGroup.class, request.getUuid()));

            certificate.setGroup(certificateGroup);
            certificateRepository.save(certificate);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateEntity(MultipleEntityUpdateDto request) throws NotFoundException {
        for (String certificateUuid : request.getCertificateUuids()) {
            Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                    .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
            CertificateEntity certificateEntity = entityRepository.findByUuid(request.getUuid())
                    .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getUuid()));
            certificate.setEntity(certificateEntity);
            certificateRepository.save(certificate);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateOwner(CertificateOwnerBulkUpdateDto request) throws NotFoundException {
        for (String certificateId : request.getCertificateUuids()) {
            Certificate certificate = certificateRepository.findByUuid(certificateId)
                    .orElseThrow(() -> new NotFoundException(Certificate.class, certificateId));
            certificate.setOwner(request.getOwner());
            certificateRepository.save(certificate);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.DELETE)
    public void bulkRemoveCertificate(RemoveCertificateDto request) throws NotFoundException {
        for (String uuid : request.getUuids()) {
            Certificate certificate = certificateRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
            if (!adminRepository.findByCertificate(certificate).isEmpty()) {
                logger.warn("Certificate tagged as admin. Unable to delete certificate with common name {}", certificate.getCommonName());
                continue;
            }

            if (!clientRepository.findByCertificate(certificate).isEmpty()) {
                logger.warn("Certificate tagged as client. Unable to delete certificate with common name {}", certificate.getCommonName());
                continue;
            }

            if (discoveryCertificateRepository.findByCertificateContent(certificate.getCertificateContent()).isEmpty()) {
                CertificateContent content = certificateContentRepository
                        .findById(certificate.getCertificateContent().getId()).orElse(null);
                if (content != null) {
                    certificateContentRepository.delete(content);
                }
            }

            certificateRepository.delete(certificate);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateIssuer() {
        for (Certificate certificate : certificateRepository.findAllByIssuerSerialNumber(null)) {
            if (certificate.getIssuerDn().equals(certificate.getSubjectDn())) {
                logger.debug("Certificate with UUID {} is self signed / CA", certificate.getUuid());
            } else {
                for (Certificate issuer : certificateRepository.findBySubjectDn(certificate.getIssuerDn())) {
                    X509Certificate subCert;
                    X509Certificate issCert;
                    try {
                        subCert = getX509(certificate.getCertificateContent().getContent());
                        issCert = getX509(issuer.getCertificateContent().getContent());
                    } catch (Exception e) {
                        continue;
                    }

                    if (verifySignature(subCert, issCert)) {
                        try {
                            X509Certificate issuerCert = CertificateUtil
                                    .parseCertificate(issuer.getCertificateContent().getContent());
                            X509Certificate subjectCert = CertificateUtil
                                    .parseCertificate(certificate.getCertificateContent().getContent());

                            try {
                                subjectCert.verify(issuerCert.getPublicKey());
                                certificate.setIssuerSerialNumber(issuer.getSerialNumber());
                                certificateRepository.save(certificate);
                            } catch (Exception e) {
                                logger.debug("Error when getting the issuer");
                            }

                        } catch (CertificateException e) {
                            logger.warn("Unable to parse the issuer with subject {}", certificate.getIssuerDn());
                        }
                    }
                }
            }
        }
    }

    private boolean verifySignature(X509Certificate subjectCertificate, X509Certificate issuerCertificate) {
        try {
            subjectCertificate.verify(issuerCertificate.getPublicKey());
            return true;
        } catch (Exception e) {
            logger.warn("Unable to verify certificate for signature.", e);
            return false;
        }
    }

    private X509Certificate getX509(String certificate) throws CertificateException {
        return CertificateUtil.getX509Certificate(certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", ""));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CREATE)
    public Certificate createCertificateEntity(X509Certificate certificate) {
        logger.debug("Making a new entry for a certificate");
        Certificate modal = new Certificate();
        String fingerprint = null;
        try {
            fingerprint = CertificateUtil.getThumbprint(certificate.getEncoded());
            Optional<Certificate> existingCertificate = certificateRepository.findByFingerprint(fingerprint);

            if (existingCertificate.isPresent()) {
                return existingCertificate.get();
            }
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            logger.error("Unable to calculate sha 256 thumbprint");
        }

        CertificateUtil.prepareCertificate(modal, certificate);
        modal.setFingerprint(fingerprint);
        modal.setCertificateContent(checkAddCertificateContent(fingerprint, X509ObjectToString.toPem(certificate)));

        return modal;
    }

    private CertificateContent checkAddCertificateContent(String fingerprint, String content) {
        CertificateContent certificateContent = certificateContentRepository.findByFingerprint(fingerprint);
        if (certificateContent != null) {
            return certificateContent;
        }

        certificateContent = new CertificateContent();
        certificateContent.setContent(CertificateUtil.normalizeCertificateContent(content));
        certificateContent.setFingerprint(fingerprint);

        certificateContentRepository.save(certificateContent);
        return certificateContent;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CREATE)
    public Certificate saveCertificateEntity(String cert) throws CertificateException {
        X509Certificate certificate = CertificateUtil.parseCertificate(cert);
        Certificate entity = createCertificateEntity(certificate);
        certificateRepository.save(entity);
        return entity;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CREATE)
    public CertificateDto upload(UploadCertificateRequestDto request)
            throws AlreadyExistException, CertificateException {
        X509Certificate certificate = CertificateUtil.parseCertificate(request.getCertificate());
        String certificateSerialNumber = certificate.getSerialNumber().toString(16);
        if (!certificateRepository.findBySerialNumberIgnoreCase(certificateSerialNumber).isEmpty()) {
            throw new AlreadyExistException("Certificate already exists with serial number " + certificateSerialNumber);
        }
        Certificate entity = createCertificateEntity(certificate);
        certificateRepository.save(entity);
        updateIssuer();
        try {
            certValidationService.validate(entity);
        } catch (Exception e) {
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }

        return entity.mapToDto();
    }

    @Override
    public Certificate checkCreateCertificate(X509Certificate certificate) throws AlreadyExistException {
        String certificateSerialNumber = certificate.getSerialNumber().toString(16);
        if (!certificateRepository.findBySerialNumberIgnoreCase(certificateSerialNumber).isEmpty()) {
            throw new AlreadyExistException("Certificate already exists with serial number " + certificateSerialNumber);
        }
        Certificate entity = createCertificateEntity(certificate);
        certificateRepository.save(entity);
        return entity;
    }

    @Override
    public Certificate checkCreateCertificate(String certificate) throws AlreadyExistException, CertificateException {
        X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate);
        String certificateSerialNumber = x509Cert.getSerialNumber().toString(16);
        if (!certificateRepository.findBySerialNumberIgnoreCase(certificateSerialNumber).isEmpty()) {
            throw new AlreadyExistException("Certificate already exists with serial number " + certificateSerialNumber);
        }
        Certificate entity = createCertificateEntity(x509Cert);
        certificateRepository.save(entity);
        return entity;
    }

    @Override
    public void revokeCertificate(String serialNumber) {
        try {
            Certificate certificate = certificateRepository.findBySerialNumberIgnoreCase(serialNumber).orElseThrow(() -> new NotFoundException(Certificate.class, serialNumber));
            certificate.setStatus(CertificateStatus.REVOKED);
            certificateRepository.save(certificate);
        } catch (NotFoundException e) {
            logger.warn("Unable to find the certificate with serialNumber {}", serialNumber);
        }
    }
}
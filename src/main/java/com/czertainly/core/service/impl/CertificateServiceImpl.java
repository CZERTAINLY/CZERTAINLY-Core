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
import com.czertainly.api.model.core.certificate.search.SearchCondition;
import com.czertainly.api.model.core.certificate.search.SearchFieldDataDto;
import com.czertainly.api.model.core.certificate.search.SearchableFieldType;
import com.czertainly.api.model.core.certificate.search.SearchableFields;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.X509ObjectToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR", "ROLE_CLIENT", "ROLE_ACME"})
public class CertificateServiceImpl implements CertificateService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateServiceImpl.class);

    private static final String COMMON_NAME_LABEL = "Common Name";
    private static final String SERIAL_NUMBER_LABEL = "Serial Number";
    private static final String RA_PROFILE_NAME_LABEL = "RA Profile";
    private static final String ENTITY_NAME_LABEL = "Entity";
    private static final String STATUS_LABEL = "Status";
    private static final String GROUP_NAME_LABEL = "Group";
    private static final String OWNER_LABEL = "Owner";
    private static final String ISSUER_COMMON_NAME_LABEL = "Issuer Common Name";
    private static final String SIGNATURE_ALGORITHM_LABEL = "Signature Algorithm";
    private static final String FINGERPRINT_LABEL = "Fingerprint";
    private static final String EXPIRES_LABEL = "Expires At";
    private static final String NOT_BEFORE_LABEL = "Valid From";
    private static final String PUBLIC_KEY_ALGORITHM_LABEL = "Public Key Algorithm";
    private static final String KEY_SIZE_LABEL = "Key Size";
    private static final String KEY_USAGE_LABEL = "Key Usage";
    private static final String BASIC_CONSTRAINTS_LABEL = "Basic Constraints";
    private static final String META_DATA_LABEL = "Meta Data";
    private static final String SUBJECT_ALTERNATIVE_NAME_LABEL = "Subject Alternative Name";
    private static final String SUBJECT_DN_LABEL = "Subject DN";
    private static final String ISSUER_DN_LABEL = "Issuer DN";
    private static final String ISSUER_SERIAL_NUMBER_LABEL = "Issuer Serial Number";
    private static final String OCSP_VALIDATION_LABEL = "OCSP Validation";
    private static final String CRL_VALIDATION_LABEL = "CRL Validation";
    private static final String SIGNATURE_VALIDATION_LABEL = "Signature Validation";

    private static final Integer DEFAULT_PAGE_SIZE = 10;
    private static final Integer MAX_PAGE_SIZE = 1000;

    @Autowired
    private EntityManagerFactory emFactory;

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
    public CertificateResponseDto listCertificates(CertificateSearchRequestDto request) throws ValidationException {
        Map<String, Integer> page = getPageable(request);
        return getCertificatesWithFilter(request, page);

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
        RaProfile raProfile = raProfileRepository.findByUuid(request.getUuid())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getUuid()));
        if (!request.isAllSelect()) {
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                certificate.setRaProfile(raProfile);
                certificateRepository.save(certificate);
            }
        } else {
            for (Certificate certificate : queryExecutor(request.getFilters())) {
                certificate.setRaProfile(raProfile);
                certificateRepository.save(certificate);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateCertificateGroup(MultipleGroupUpdateDto request) throws NotFoundException {
        CertificateGroup certificateGroup = groupRepository.findByUuid(request.getUuid())
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, request.getUuid()));

        if (!request.isAllSelect()) {
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                certificate.setGroup(certificateGroup);
                certificateRepository.save(certificate);
            }
        } else {
            for (Certificate certificate : queryExecutor(request.getFilters())) {
                certificate.setGroup(certificateGroup);
                certificateRepository.save(certificate);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateEntity(MultipleEntityUpdateDto request) throws NotFoundException {
        CertificateEntity certificateEntity = entityRepository.findByUuid(request.getUuid())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getUuid()));
        if (!request.isAllSelect()) {
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                certificate.setEntity(certificateEntity);
                certificateRepository.save(certificate);
            }
        } else {
            for (Certificate certificate : queryExecutor(request.getFilters())) {
                certificate.setEntity(certificateEntity);
                certificateRepository.save(certificate);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateOwner(CertificateOwnerBulkUpdateDto request) throws NotFoundException {
        if (!request.isAllSelect()) {
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                certificate.setOwner(request.getOwner());
                certificateRepository.save(certificate);
            }
        } else {
            for (Certificate certificate : queryExecutor(request.getFilters())) {
                certificate.setOwner(request.getOwner());
                certificateRepository.save(certificate);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.DELETE)
    public List<String> bulkRemoveCertificate(RemoveCertificateDto request) throws NotFoundException {
        List<String> failedDeleteCerts = new ArrayList<>();
        if (!request.isAllSelect()) {
            for (String uuid : request.getUuids()) {
                Certificate certificate = certificateRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
                if (!adminRepository.findByCertificate(certificate).isEmpty()) {
                    logger.warn("Certificate tagged as admin. Unable to delete certificate with common name {}", certificate.getCommonName());
                    failedDeleteCerts.add(certificate.getUuid());
                    continue;
                }

                if (!clientRepository.findByCertificate(certificate).isEmpty()) {
                    logger.warn("Certificate tagged as client. Unable to delete certificate with common name {}", certificate.getCommonName());
                    failedDeleteCerts.add(certificate.getUuid());
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
        } else {
            for (Certificate certificate : queryExecutor(request.getFilters())) {
                if (!adminRepository.findByCertificate(certificate).isEmpty()) {
                    logger.warn("Certificate tagged as admin. Unable to delete certificate with common name {}", certificate.getCommonName());
                    failedDeleteCerts.add(certificate.getCommonName());
                    continue;
                }

                if (!clientRepository.findByCertificate(certificate).isEmpty()) {
                    logger.warn("Certificate tagged as client. Unable to delete certificate with common name {}", certificate.getCommonName());
                    failedDeleteCerts.add(certificate.getCommonName());
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
        return failedDeleteCerts;
    }

    @Override
    public List<SearchFieldDataDto> getSearchableFieldInformation() {
        return getSearchableFieldsMap();
    }

    @Override
    public List<SearchFieldDataDto> getSearchableFieldInformation() {
        return getSearchableFieldsMap();
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

    private List<SearchFieldDataDto> getSearchableFieldsMap() {
        List<SearchFieldDataDto> fields = new ArrayList<>();

        fields.add(getSearchField(SearchableFields.COMMON_NAME,
                        COMMON_NAME_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.SERIAL_NUMBER,
                        SERIAL_NUMBER_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.ISSUER_SERIAL_NUMBER,
                        ISSUER_SERIAL_NUMBER_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH
                        )
                )
        );


        fields.add(getSearchField(SearchableFields.RA_PROFILE_NAME,
                        RA_PROFILE_NAME_LABEL,
                        true,
                        raProfileRepository.findAll().stream().map(RaProfile::getName).collect(Collectors.toList()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS, SearchCondition.EMPTY, SearchCondition.NOT_EMPTY)
                )
        );

        fields.add(getSearchField(SearchableFields.ENTITY_NAME,
                        ENTITY_NAME_LABEL,
                        true,
                        entityRepository.findAll().stream().map(CertificateEntity::getName).collect(Collectors.toList()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS, SearchCondition.EMPTY, SearchCondition.NOT_EMPTY)
                )
        );

        fields.add(getSearchField(SearchableFields.OWNER,
                        OWNER_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH)
                )
        );

        fields.add(getSearchField(SearchableFields.STATUS,
                        STATUS_LABEL,
                        true,
                        List.of(CertificateStatus.REVOKED.toString(),
                                CertificateStatus.EXPIRED.toString(),
                                CertificateStatus.EXPIRING.toString(),
                                CertificateStatus.VALID.toString(),
                                CertificateStatus.INVALID.toString(),
                                CertificateStatus.NEW.toString(),
                                CertificateStatus.UNKNOWN.toString()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS)
                )
        );

        fields.add(getSearchField(SearchableFields.GROUP_NAME,
                        GROUP_NAME_LABEL,
                        true,
                        groupRepository.findAll().stream().map(CertificateGroup::getName).collect(Collectors.toList()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS, SearchCondition.EMPTY, SearchCondition.NOT_EMPTY)
                )
        );

        fields.add(getSearchField(SearchableFields.ISSUER_COMMON_NAME,
                        ISSUER_COMMON_NAME_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.FINGERPRINT,
                        FINGERPRINT_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.SIGNATURE_ALGORITHM,
                        SIGNATURE_ALGORITHM_LABEL,
                        true,
                        new ArrayList<>(certificateRepository.findDistinctSignatureAlgorithm()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.NOT_AFTER,
                        EXPIRES_LABEL,
                        false,
                        null,
                        SearchableFieldType.DATE,
                        List.of(SearchCondition.GREATER,
                                SearchCondition.LESSER
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.NOT_BEFORE,
                        NOT_BEFORE_LABEL,
                        false,
                        null,
                        SearchableFieldType.DATE,
                        List.of(SearchCondition.GREATER,
                                SearchCondition.LESSER
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.SUBJECTDN,
                        SUBJECT_DN_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.ISSUERDN,
                        ISSUER_DN_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EQUALS,
                                SearchCondition.NOT_EQUALS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY,
                                SearchCondition.STARTS_WITH,
                                SearchCondition.ENDS_WITH
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.META,
                        META_DATA_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.SUBJECT_ALTERNATIVE_NAMES,
                        SUBJECT_ALTERNATIVE_NAME_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.CONTAINS,
                                SearchCondition.NOT_CONTAINS,
                                SearchCondition.EMPTY,
                                SearchCondition.NOT_EMPTY
                        )
                )
        );

        fields.add(getSearchField(SearchableFields.PUBLIC_KEY_ALGORITHM,
                        PUBLIC_KEY_ALGORITHM_LABEL,
                        true,
                        new ArrayList<>(certificateRepository.findDistinctPublicKeyAlgorithm()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS)
                )
        );

        fields.add(getSearchField(SearchableFields.KEY_SIZE,
                        KEY_SIZE_LABEL,
                        true,
                        new ArrayList<>(certificateRepository.findDistinctKeySize()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS)
                )
        );

        fields.add(getSearchField(SearchableFields.BASIC_CONSTRAINTS,
                        BASIC_CONSTRAINTS_LABEL,
                        true,
                        new ArrayList<>(certificateRepository.findDistinctBasicConstraints()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS)
                )
        );

        fields.add(getSearchField(SearchableFields.KEY_USAGE,
                        KEY_USAGE_LABEL,
                        false,
                        serializedListOfStringToListOfObject(certificateRepository.findDistinctKeyUsage()),
                        SearchableFieldType.LIST,
                        List.of(SearchCondition.CONTAINS, SearchCondition.NOT_CONTAINS)
                )
        );

        fields.add(getSearchField(SearchableFields.OCSP_VALIDATION,
                        OCSP_VALIDATION_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.SUCCESS, SearchCondition.FAILED, SearchCondition.UNKNOWN, SearchCondition.EMPTY)
                )
        );

        fields.add(getSearchField(SearchableFields.CRL_VALIDATION,
                        CRL_VALIDATION_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.SUCCESS, SearchCondition.FAILED, SearchCondition.UNKNOWN, SearchCondition.EMPTY)
                )
        );

        fields.add(getSearchField(SearchableFields.SIGNATURE_VALIDATION,
                        SIGNATURE_VALIDATION_LABEL,
                        false,
                        null,
                        SearchableFieldType.STRING,
                        List.of(SearchCondition.SUCCESS, SearchCondition.FAILED, SearchCondition.UNKNOWN)
                )
        );
        logger.debug("Searchable Fields: {}", fields);
        return fields;
    }

    private List<Object> serializedListOfStringToListOfObject(List<String> serializedData) {
        Set<String> serSet = new LinkedHashSet<>();
        for (String obj : serializedData) {
            serSet.addAll(MetaDefinitions.deserializeArrayString(obj));
        }
        return new ArrayList<>(serSet);
    }

    private SearchFieldDataDto getSearchField(SearchableFields field, String label, Boolean multiValue, List<Object> values,
                                              SearchableFieldType fieldType, List<SearchCondition> conditions) {
        SearchFieldDataDto dto = new SearchFieldDataDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setMultiValue(multiValue);
        dto.setValue(values);
        dto.setType(fieldType);
        dto.setConditions(conditions);
        return dto;
    }

    private Map<String, Integer> getPageable(CertificateSearchRequestDto request) throws ValidationException {
        if (request.getItemsPerPage() == null) {
            request.setItemsPerPage(DEFAULT_PAGE_SIZE);
        }
        if (request.getItemsPerPage() > MAX_PAGE_SIZE) {
            throw new ValidationException(ValidationError.create("Maximum items per page is " + MAX_PAGE_SIZE));
        }

        Integer pageStart = 0;
        Integer pageEnd = request.getItemsPerPage();

        if (request.getPageNumber() != null) {
            pageStart = ((request.getPageNumber() - 1) * request.getItemsPerPage());
            pageEnd = request.getPageNumber() * request.getItemsPerPage();
        }
        logger.debug("Pagination information - Start: {}, End : {}", pageStart, pageEnd);
        return Map.ofEntries(Map.entry("start", pageStart), Map.entry("end", pageEnd));
    }

    private CertificateResponseDto getCertificatesWithFilter(CertificateSearchRequestDto request, Map<String, Integer> page) {
        logger.debug("Certificate search request: {}", request.toString());
        CertificateResponseDto certificateResponseDto = new CertificateResponseDto();
        certificateResponseDto.setPageNumber(request.getPageNumber());
        certificateResponseDto.setItemsPerPage(request.getItemsPerPage());

        if (request.getFilters() == null || request.getFilters().isEmpty()) {
            Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
            certificateResponseDto.setTotalPages((int) Math.ceil((double) certificateRepository.count() / request.getItemsPerPage()));
            certificateResponseDto.setTotalItems(certificateRepository.count());
            certificateResponseDto.setCertificates(certificateRepository.findAllByOrderByIdDesc(p).stream().map(Certificate::mapToDto).collect(Collectors.toList()));
        } else {
            String sqlQuery = getQueryDynamicBasedOnFilter(request.getFilters());
            EntityManager entityManager = emFactory.createEntityManager();
            Query query = entityManager.createQuery(sqlQuery);
            query.setFirstResult(page.get("start"));
            query.setMaxResults(request.getItemsPerPage());
            List<Certificate> certificates = query.getResultList();

            if (certificates.isEmpty()) {
                certificateResponseDto.setTotalPages(1);
                certificateResponseDto.setTotalItems(0L);
                certificateResponseDto.setCertificates(new ArrayList<>());
                entityManager.close();
            } else {
                Query countQuery = entityManager.createQuery(sqlQuery.replace("select c from", "select COUNT(c) from").replace(" GROUP BY c.id ORDER BY c.id DESC", ""));
                Long totalItems = (Long) countQuery.getSingleResult();
                certificateResponseDto.setTotalPages((int) Math.ceil((double) totalItems / request.getItemsPerPage()));
                certificateResponseDto.setTotalItems(totalItems);
                entityManager.close();
                certificateResponseDto.setCertificates(certificates.stream().map(Certificate::mapToDto).collect(Collectors.toList()));
            }
        }
        if (certificateResponseDto.getTotalPages().equals(0)) {
            certificateResponseDto.setTotalPages(1);
        }
        if (certificateResponseDto.getTotalPages().equals(0)) {
            certificateResponseDto.setTotalPages(1);
        }
        return certificateResponseDto;
    }

    private String getQueryDynamicBasedOnFilter(List<CertificateFilterRequestDto> conditions) throws ValidationException {
        String query = "select c from Certificate c WHERE";
        List<String> queryParts = new ArrayList<>();
        List<SearchFieldDataDto> originalJson = getSearchableFieldsMap();
        List<SearchFieldDataDto> iterableJson = new LinkedList<>();
        for (CertificateFilterRequestDto requestField : conditions) {
            for (SearchFieldDataDto field : originalJson) {
                if (requestField.getField().equals(field.getField())) {
                    SearchFieldDataDto fieldDup = new SearchFieldDataDto();
                    fieldDup.setField(field.getField());
                    fieldDup.setType(field.getType());
                    fieldDup.setLabel(field.getLabel());
                    fieldDup.setType(field.getType());
                    fieldDup.setMultiValue(field.isMultiValue());
                    fieldDup.setValue(requestField.getValue());
                    fieldDup.setConditions(List.of(requestField.getCondition()));
                    iterableJson.add(fieldDup);
                }
            }
        }
        for (SearchFieldDataDto filter : iterableJson) {
            String qp = "";
            if(List.of(SearchableFields.OCSP_VALIDATION, SearchableFields.CRL_VALIDATION, SearchableFields.SIGNATURE_VALIDATION).contains(filter.getField())){
                qp += " c.certificateValidationResult ";
            }else {
                qp += " c." + filter.getField().getCode() + " ";
            }
            if (filter.isMultiValue() && !(filter.getValue() instanceof String)) {
                List<String> whereObjects = new ArrayList<>();
                if (filter.getField().equals(SearchableFields.RA_PROFILE_NAME)) {
                    whereObjects.addAll(raProfileRepository.findAll().stream().filter(c -> ((List<Object>) filter.getValue()).contains(c.getName())).map(RaProfile::getId).map(i -> i.toString()).collect(Collectors.toList()));
                } else if (filter.getField().equals(SearchableFields.ENTITY_NAME)) {
                    whereObjects.addAll(entityRepository.findAll().stream().filter(c -> ((List<Object>) filter.getValue()).contains(c.getName())).map(CertificateEntity::getId).map(i -> i.toString()).collect(Collectors.toList()));
                } else if (filter.getField().equals(SearchableFields.GROUP_NAME)) {
                    whereObjects.addAll(groupRepository.findAll().stream().filter(c -> ((List<Object>) filter.getValue()).contains(c.getName())).map(CertificateGroup::getId).map(i -> i.toString()).collect(Collectors.toList()));
                } else {
                    whereObjects.addAll(((List<Object>) filter.getValue()).stream().map(i -> "'" + i.toString() + "'").collect(Collectors.toList()));
                }

                if (whereObjects.isEmpty()) {
                    throw new ValidationException(ValidationError.create("No valid object found for search in " + filter.getLabel()));
                }

                if (filter.getConditions().get(0).equals(SearchCondition.EQUALS)) {
                    qp += " IN (" + String.join(",", whereObjects) + " )";
                }
                if (filter.getConditions().get(0).equals(SearchCondition.NOT_EQUALS)) {
                    qp += " NOT IN (" + String.join(",", whereObjects) + " )";
                }
            } else {
                if(filter.getField().equals(SearchableFields.SIGNATURE_VALIDATION)){
                    if(filter.getConditions().get(0).equals(SearchCondition.SUCCESS)){
                        qp += " LIKE '%\"Signature Verification\":{\"status\":\"success\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.FAILED)){
                        qp += " LIKE '%\"Signature Verification\":{\"status\":\"failed\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.UNKNOWN)){
                        qp += " LIKE '%\"Signature Verification\":{\"status\":\"not_checked\"%'";
                    }
                }else if(filter.getField().equals(SearchableFields.OCSP_VALIDATION)){
                    if(filter.getConditions().get(0).equals(SearchCondition.SUCCESS)){
                        qp += "LIKE '\"OCSP Verification\":{\"status\":\"success\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.FAILED)){
                        qp += "LIKE '%\"OCSP Verification\":{\"status\":\"failed\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.UNKNOWN)){
                        qp += "LIKE '%\"OCSP Verification\":{\"status\":\"unknown\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.EMPTY)){
                        qp += "LIKE '%\"OCSP Verification\":{\"status\":\"warning\"%'";
                    }
                }
                else if(filter.getField().equals(SearchableFields.CRL_VALIDATION)){
                    if(filter.getConditions().get(0).equals(SearchCondition.SUCCESS)){
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"success\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.FAILED)){
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"failed\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.UNKNOWN)){
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"unknown\"%'";
                    }else if(filter.getConditions().get(0).equals(SearchCondition.EMPTY)){
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"warning\"%'";
                    }
                }
                else if (filter.getConditions().get(0).equals(SearchCondition.CONTAINS) || filter.getConditions().get(0).equals(SearchCondition.NOT_CONTAINS)) {
                    qp += filter.getConditions().get(0).getCode() + " '%" + filter.getValue().toString() + "%'";
                } else if (filter.getConditions().get(0).equals(SearchCondition.STARTS_WITH)) {
                    qp += filter.getConditions().get(0).getCode() + " '" + filter.getValue().toString() + "%'";
                } else if (filter.getConditions().get(0).equals(SearchCondition.ENDS_WITH)) {
                    qp += filter.getConditions().get(0).getCode() + " '%" + filter.getValue().toString() + "'";
                } else if (filter.getConditions().get(0).equals(SearchCondition.EMPTY) || filter.getConditions().get(0).equals(SearchCondition.NOT_EMPTY)) {
                    qp += filter.getConditions().get(0).getCode();
                } else {
                    if (filter.getField().equals(SearchableFields.RA_PROFILE_NAME)) {
                        String raProfileId = raProfileRepository.findByName(filter.getValue().toString()).orElseThrow(() -> new ValidationException(ValidationError.create(filter.getValue().toString() + " not found"))).getId().toString();
                        qp += filter.getConditions().get(0).getCode() + " '" + raProfileId + "'";
                    } else if (filter.getField().equals(SearchableFields.ENTITY_NAME)) {
                        String entityId = entityRepository.findByName(filter.getValue().toString()).orElseThrow(() -> new ValidationException(ValidationError.create(filter.getValue().toString() + " not found"))).getId().toString();
                        qp += filter.getConditions().get(0).getCode() + " '" + entityId + "'";
                    } else if (filter.getField().equals(SearchableFields.GROUP_NAME)) {
                        String groupId = groupRepository.findByName(filter.getValue().toString()).orElseThrow(() -> new ValidationException(ValidationError.create(filter.getValue().toString() + " not found"))).getId().toString();
                        qp += filter.getConditions().get(0).getCode() + " '" + groupId + "'";
                    } else {
                        qp += filter.getConditions().get(0).getCode() + " '" + filter.getValue().toString() + "'";
                    }
                }
            }
            queryParts.add(qp);
        }
        query += String.join(" AND ", queryParts);
        query += " GROUP BY c.id ORDER BY c.id DESC";
        logger.debug("Executable query: {}", query);
        return query;
    }

    private List<Certificate> queryExecutor(List<CertificateFilterRequestDto> filters) {
        String sqlQuery = "select c from Certificate c";
        logger.debug("Executing query: {}", sqlQuery);
        if (!filters.isEmpty()) {
            sqlQuery = getQueryDynamicBasedOnFilter(filters);
        }
        EntityManager entityManager = emFactory.createEntityManager();
        Query query = entityManager.createQuery(sqlQuery);
        return query.getResultList();
    }
}
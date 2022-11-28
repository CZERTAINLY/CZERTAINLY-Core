package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.common.AuthenticationServiceExceptionDto;
import com.czertainly.api.model.common.attribute.v2.InfoAttribute;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.compliance.ComplianceRuleStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.DynamicSearchInternalResponse;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.search.SearchLabelConstants;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.security.exception.AuthenticationServiceException;
import com.czertainly.core.service.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.OcspUtil;
import com.czertainly.core.util.X509ObjectToString;
import com.google.common.collect.Lists;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
public class CertificateServiceImpl implements CertificateService {

    // Default page size for the certificate search API when page size is not provided
    public static final Integer DEFAULT_PAGE_SIZE = 10;
    // Maximum page size for search API operation
    public static final Integer MAX_PAGE_SIZE = 1000;
    // Default batch size to perform bulk delete operation on Certificates
    public static final Integer DELETE_BATCH_SIZE = 1000;
    private static final Logger logger = LoggerFactory.getLogger(CertificateServiceImpl.class);

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private RaProfileService raProfileService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private CertValidationService certValidationService;

    @Autowired
    private CertificateEventHistoryService certificateEventHistoryService;

    @Autowired
    private SearchService searchService;

    @Lazy
    @Autowired
    private LocationService locationService;

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private AttributeService attributeService;


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.LIST, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.LIST)
    public CertificateResponseDto listCertificates(SecurityFilter filter, SearchRequestDto request) throws ValidationException {
        filter.setParentRefProperty("raProfileUuid");
        return getCertificatesWithFilter(request, filter);

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DETAIL)
    public CertificateDto getCertificate(SecuredUUID uuid) throws NotFoundException, CertificateException, IOException {
        Certificate entity = getCertificateEntity(uuid);
        CertificateDto dto = entity.mapToDto();
        if (entity.getComplianceResult() != null) {
            dto.setNonCompliantRules(frameComplianceResult(entity.getComplianceResult()));
        } else {
            dto.setComplianceStatus(ComplianceStatus.NA);
        }
        dto.setMetadata(metadataService.getFullMetadata(entity.getUuid(), Resource.CERTIFICATE, null, null));
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.CERTIFICATE));
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DETAIL)
    public Certificate getCertificateEntity(SecuredUUID uuid) throws NotFoundException {
        Certificate entity = certificateRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        if (entity.getRaProfileUuid() != null) {
            raProfileService.getRaProfile(SecuredUUID.fromUUID(entity.getRaProfileUuid()));
        } else {
            if (!raProfileService.evaluateNullableRaPermissions(SecurityFilter.create())) {
                AuthenticationServiceExceptionDto dto = new AuthenticationServiceExceptionDto();
                dto.setStatusCode(403);
                dto.setCode("ACCESS_DENIED");
                dto.setMessage("Access Denied. Certificate does not have any RA Profile association. Required 'Detail' permission for all 'Ra Profiles'");
                throw new AuthenticationServiceException(dto);
            }
        }
        return entity;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    // This method does not need security as it is not exposed by the controllers. This method also does not uses uuid
    public Certificate getCertificateEntityByContent(String content) {
        CertificateContent certificateContent = certificateContentRepository.findByContent(content);
        return certificateRepository.findByCertificateContent(certificateContent);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    //This method does not need security as it is used only by the internal services for certificate related operations
    public Certificate getCertificateEntityByFingerprint(String fingerprint) throws NotFoundException {
        return certificateRepository.findByFingerprint(fingerprint)
                .orElseThrow(() -> new NotFoundException(Certificate.class, fingerprint));
    }

    @Override
    public Boolean checkCertificateExistsByFingerprint(String fingerprint) {
        try {
            return certificateRepository.findByFingerprint(fingerprint).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DELETE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteCertificate(SecuredUUID uuid) throws NotFoundException {
        Certificate certificate = getCertificateEntity(uuid);

        List<ValidationError> errors = new ArrayList<>();

        if (certificate.getUserUuid() != null) {
            errors.add(ValidationError.create("Certificate is used by an User"));
            certificateEventHistoryService.addEventHistory(CertificateEvent.DELETE, CertificateEventStatus.FAILED, "Certificate is used by an User", "", certificate);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete certificate", errors);
        }

        // remove certificate from Locations
        try {
            locationService.removeCertificateFromLocations(uuid);
        } catch (ConnectorException e) {
            logger.error("Failed to remove Certificate {} from Locations", uuid);
        }

        if (discoveryCertificateRepository.findByCertificateContent(certificate.getCertificateContent()).isEmpty()) {
            CertificateContent content = certificateContentRepository
                    .findById(certificate.getCertificateContent().getId()).orElse(null);
            if (content != null) {
                certificateRepository.delete(certificate);
                certificateContentRepository.delete(content);
            }
        } else {
            certificateRepository.delete(certificate);
        }
        attributeService.deleteAttributeContent(uuid.getValue(), Resource.CERTIFICATE);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.UPDATE)
    public void updateCertificateObjects(SecuredUUID uuid, CertificateUpdateObjectsDto request) throws NotFoundException {
        if (request.getRaProfileUuid() != null) {
            updateRaProfile(uuid, SecuredUUID.fromString(request.getRaProfileUuid()));
        }
        if (request.getGroupUuid() != null) {
            updateCertificateGroup(uuid, SecuredUUID.fromString(request.getGroupUuid()));
        }
        if (request.getOwner() != null) {
            updateOwner(uuid, request.getOwner());
        }
    }

    @Async
    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.UPDATE, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.DETAIL)
    public void bulkUpdateCertificateObjects(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException {
        filter.setParentRefProperty("raProfileUuid");
        if (request.getRaProfileUuid() != null) {
            bulkUpdateRaProfile(filter, request);
        }
        if (request.getGroupUuid() != null) {
            bulkUpdateCertificateGroup(filter, request);
        }
        if (request.getOwner() != null) {
            bulkUpdateOwner(filter, request);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.DELETE)
    @Async("threadPoolTaskExecutor")
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DELETE, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.DETAIL)
    public void bulkDeleteCertificate(SecurityFilter filter, RemoveCertificateDto request) throws NotFoundException {
        filter.setParentRefProperty("raProfileUuid");
        List<String> failedDeleteCerts = new ArrayList<>();
        Integer totalItems;
        BulkOperationResponse bulkOperationResponse = new BulkOperationResponse();
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null) {
            for (String uuid : request.getUuids()) {
                try {
                    deleteCertificate(SecuredUUID.fromString(uuid));
                } catch (Exception e) {
                    logger.error("Unable to delete the certificate.", e.getMessage());
                }
            }
        } else {
            List<Certificate> certList = (List<Certificate>) searchService.completeSearchQueryExecutor(request.getFilters(), "Certificate", getSearchableFieldInformation());
            totalItems = certList.size();

            String joins = "WHERE c.user_uuid IS NULL";
            String data = searchService.createCriteriaBuilderString(filter, true);
            if (!data.equals("")) {
                joins = joins + " AND " + data;
            }

            String customQuery = searchService.getQueryDynamicBasedOnFilter(request.getFilters(), "Certificate", getSearchableFieldInformation(), joins, false, false, "");

            List<Certificate> certListDyn = (List<Certificate>) searchService.customQueryExecutor(customQuery);

            bulkOperationResponse.setFailedItem(Long.valueOf(totalItems - certListDyn.size()));

            for (List<Certificate> certificates : Lists.partition(certListDyn, DELETE_BATCH_SIZE)) {
                certificateRepository.deleteAll(certificates);
            }
            for (List<CertificateContent> certificateContents : Lists.partition(certificateContentRepository.findCertificateContentNotUsed(), DELETE_BATCH_SIZE)) {
                certificateContentRepository.deleteAll(certificateContents);
            }
        }
        certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
    }

    @Override
    public List<SearchFieldDataDto> getSearchableFieldInformation() {
        return getSearchableFieldsMap();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    //Auth is not required for this methods. It is only internally used by other services to update the issuers of the certificate
    public void updateIssuer() {
        for (Certificate certificate : certificateRepository.findAllByIssuerSerialNumber(null)) {
            if (!certificate.getIssuerDn().equals(certificate.getSubjectDn())) {
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
            return false;
        }
    }

    private X509Certificate getX509(String certificate) throws CertificateException {
        return CertificateUtil.getX509Certificate(certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("\r", "").replace("\n", "").replace("-----END CERTIFICATE-----", ""));
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.CREATE)
    public Certificate createCertificate(String certificateData, CertificateType certificateType) throws com.czertainly.api.exception.CertificateException {
        Certificate entity = new Certificate();
        String fingerprint;

        // by default we are working with the X.509 certificate
        if (certificateType == null) {
            certificateType = CertificateType.X509;
        }
        if (!certificateType.equals(CertificateType.X509)) {
            String message = "Unsupported type of the certificate: " + certificateType;
            logger.debug(message);
            throw new com.czertainly.api.exception.CertificateException(message);
        } else {
            X509Certificate certificate;
            try {
                certificate = getX509(certificateData);
            } catch (CertificateException e) {
                String message = "Failed to get parse the certificate " + certificateData + " > " + e.getMessage();
                logger.error("message");
                throw new com.czertainly.api.exception.CertificateException(message);
            }
            try {
                fingerprint = CertificateUtil.getThumbprint(certificate.getEncoded());
                Optional<Certificate> existingCertificate = certificateRepository.findByFingerprint(fingerprint);

                if (existingCertificate.isPresent()) {
                    logger.debug("Returning existing certificate with fingerprint {}", fingerprint);
                    return existingCertificate.get();
                }
            } catch (NoSuchAlgorithmException | CertificateException e) {
                String message = "Failed to get thumbprint for certificate " + certificate.getSerialNumber() + " > " + e.getMessage();
                logger.error("message");
                throw new com.czertainly.api.exception.CertificateException(message);
            }

            CertificateUtil.prepareCertificate(entity, certificate);
            entity.setFingerprint(fingerprint);
            entity.setCertificateContent(checkAddCertificateContent(fingerprint, X509ObjectToString.toPem(certificate)));

            try {
                downloadUploadChain(entity);
                certValidationService.validate(entity);
            } catch (Exception e) {
                logger.warn("Unable to validate certificate {}, {}", entity.getUuid(), e.getMessage());
            }

            certificateRepository.save(entity);
            certificateComplianceCheck(entity);
            certificateEventHistoryService.addEventHistory(CertificateEvent.UPLOAD, CertificateEventStatus.SUCCESS, "Certificate uploaded", "", entity);

            return entity;
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.CREATE)
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
        CertificateContent certificateContent = checkAddCertificateContent(fingerprint, X509ObjectToString.toPem(certificate));
        modal.setFingerprint(fingerprint);
        modal.setCertificateContent(certificateContent);
        modal.setCertificateContentId(certificateContent.getId());

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
    public CertificateDto upload(UploadCertificateRequestDto request)
            throws AlreadyExistException, CertificateException, NoSuchAlgorithmException {
        X509Certificate certificate = CertificateUtil.parseCertificate(request.getCertificate());
        String fingerprint = CertificateUtil.getThumbprint(certificate);
        if (certificateRepository.findByFingerprint(fingerprint).isPresent()) {
            throw new AlreadyExistException("Certificate already exists with fingerprint " + fingerprint);
        }
        Certificate entity = createCertificateEntity(certificate);
        certificateRepository.save(entity);
        try {
            downloadUploadChain(entity);
            certValidationService.validate(entity);
        } catch (Exception e) {
            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
        }
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPLOAD, CertificateEventStatus.SUCCESS, "Certificate uploaded", "", entity);
        return entity.mapToDto();
    }

    @Override
    public Certificate checkCreateCertificate(String certificate) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException {
        X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate);
        String fingerprint = CertificateUtil.getThumbprint(x509Cert);
        if (certificateRepository.findByFingerprint(fingerprint).isPresent()) {
            throw new AlreadyExistException("Certificate already exists with serial number " + fingerprint);
        }
        Certificate entity = createCertificateEntity(x509Cert);
        certificateRepository.save(entity);
        certificateComplianceCheck(entity);
        return entity;
    }

    @Override
    public Certificate checkCreateCertificateWithMeta(String certificate, List<InfoAttribute> meta) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException {
        X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate);
        String fingerprint = CertificateUtil.getThumbprint(x509Cert);
        if (certificateRepository.findByFingerprint(fingerprint).isPresent()) {
            throw new AlreadyExistException("Certificate already exists with fingerprint " + fingerprint);
        }
        Certificate entity = createCertificateEntity(x509Cert);
        certificateRepository.save(entity);
        metadataService.createMetadataDefinitions(null, meta);
        metadataService.createMetadata(null, entity.getUuid(), null, meta, Resource.CERTIFICATE, null);
        certificateComplianceCheck(entity);
        return entity;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.REVOKE)
    public void revokeCertificate(String serialNumber) {
        try {
            Certificate certificate = certificateRepository.findBySerialNumberIgnoreCase(serialNumber).orElseThrow(() -> new NotFoundException(Certificate.class, serialNumber));
            certificate.setStatus(CertificateStatus.REVOKED);
            certificateRepository.save(certificate);
        } catch (NotFoundException e) {
            logger.warn("Unable to find the certificate with serialNumber {}", serialNumber);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DETAIL)
    // TODO - Enhance method to return data from location service using filter
    public List<LocationDto> listLocations(SecuredUUID certificateUuid) throws NotFoundException {
        Certificate certificateEntity = certificateRepository.findByUuid(certificateUuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));

        List<String> locations = locationService.listLocations(SecurityFilter.create(), null)
                .stream()
                .map(LocationDto::getUuid)
                .collect(Collectors.toList());

        List<LocationDto> locationsCertificate = certificateEntity.getLocations().stream()
                .map(CertificateLocation::getLocation)
                .sorted(Comparator.comparing(Location::getCreated).reversed())
                .map(Location::mapToDtoSimple)
                .filter(e -> locations.contains(e.getUuid()))
                .collect(Collectors.toList());

        return locationsCertificate;

    }


    @Override
    // Only Internal method
    public List<Certificate> listCertificatesForRaProfile(RaProfile raProfile) {
        return certificateRepository.findByRaProfile(raProfile);
    }

    @Override
    @Async
    public void checkCompliance(CertificateComplianceCheckDto request) {
        for (String uuid : request.getCertificateUuids()) {
            try {
                complianceService.checkComplianceOfCertificate(getCertificateEntity(SecuredUUID.fromString(uuid)));
            } catch (ConnectorException e) {
                logger.error("Compliance check failed.", e);
            }
        }
    }

    @Override
    // Internal Use only
    public void updateCertificateEntity(Certificate certificate) {
        certificateRepository.save(certificate);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DETAIL)
    public Map<String, CertificateValidationDto> getCertificateValidationResult(SecuredUUID uuid) throws NotFoundException {
        String validationResult = getCertificateEntity(uuid).getCertificateValidationResult();
        try {
            return MetaDefinitions.deserializeValidation(validationResult);
        } catch (IllegalStateException e) {
            logger.error(e.getMessage());
        }
        return new HashMap<>();
    }

    @Override
    // Internal Use Only
    public void updateCertificateUser(UUID certificateUuid, String userUuid) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(certificateUuid).orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
        if (userUuid == null) {
            certificate.setUserUuid(null);
        } else {
            certificate.setUserUuid(UUID.fromString(userUuid));
        }
        certificateRepository.save(certificate);
    }

    @Override
    // Internal Use Only
    public void removeCertificateUser(UUID userUuid) {
        try {
            Certificate certificate = certificateRepository.findByUserUuid(userUuid).orElseThrow(() -> new NotFoundException(Certificate.class, userUuid));
            certificate.setUserUuid(null);
            certificateRepository.save(certificate);
        } catch (NotFoundException e) {
            logger.warn("No Certificate found for the user with UUID {}", userUuid);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.LIST, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.LIST)
    public Long statisticsCertificateCount(SecurityFilter filter) {
        filter.setParentRefProperty("raProfileUuid");
        return certificateRepository.countUsingSecurityFilter(filter);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.LIST, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.LIST)
    public StatisticsDto addCertificateStatistics(SecurityFilter filter, StatisticsDto dto) {
        filter.setParentRefProperty("raProfileUuid");
        List<Certificate> certificates = certificateRepository.findUsingSecurityFilter(filter);
        //Compliance Mapping
        Map<String, String> complianceMap = new HashMap<>();
        complianceMap.put("NA", "Not Checked");
        complianceMap.put("OK", "Compliant");
        complianceMap.put("NOK", "Non Compliant");
        //
        Map<String, Long> groupStat = new HashMap<>();
        Map<String, Long> raProfileStat = new HashMap<>();
        Map<String, Long> typeStat = new HashMap<>();
        Map<String, Long> keySizeStat = new HashMap<>();
        Map<String, Long> bcStat = new HashMap<>();
        Map<String, Long> expiryStat = new HashMap<>();
        Map<String, Long> statusStat = new HashMap<>();
        Map<String, Long> complianceStat = new HashMap<>();
        Date currentTime = new Date();
        for (Certificate certificate : certificates) {
            groupStat.merge(certificate.getGroup() != null ? certificate.getGroup().getName() : "Unknown", 1L, Long::sum);
            raProfileStat.merge(certificate.getRaProfile() != null ? certificate.getRaProfile().getName() : "Unknown", 1L, Long::sum);
            typeStat.merge(certificate.getCertificateType().getCode(), 1L, Long::sum);
            keySizeStat.merge(certificate.getKeySize().toString(), 1L, Long::sum);
            bcStat.merge(certificate.getBasicConstraints(), 1L, Long::sum);
            expiryStat.merge(getExpiryTime(currentTime, certificate.getNotAfter()), 1L, Long::sum);
            statusStat.merge(certificate.getStatus().getCode(), 1L, Long::sum);
            complianceStat.merge(certificate.getComplianceStatus() != null ? complianceMap.get(certificate.getComplianceStatus().getCode().toUpperCase()) : "Not Checked", 1L, Long::sum);
        }
        dto.setGroupStatByCertificateCount(groupStat);
        dto.setRaProfileStatByCertificateCount(raProfileStat);
        dto.setCertificateStatByType(typeStat);
        dto.setCertificateStatByKeySize(keySizeStat);
        dto.setCertificateStatByBasicConstraints(bcStat);
        dto.setCertificateStatByExpiry(expiryStat);
        dto.setCertificateStatByStatus(statusStat);
        dto.setCertificateStatByComplianceStatus(complianceStat);
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.CREATE)
    public void checkIssuePermissions() {
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.RENEW)
    public void checkRenewPermissions() {
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.REVOKE)
    public void checkRevokePermissions() {
    }

    private String getExpiryTime(Date now, Date expiry) {
        long diffInMillies = now.getTime() - expiry.getTime();
        long difference = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
        if (difference <= 0) {
            return "expired";
        } else if (difference < 10) {
            return "10";
        } else if (difference < 20) {
            return "20";
        } else if (difference < 30) {
            return "30";
        } else if (difference < 60) {
            return "60";
        } else if (difference < 90) {
            return "90";
        }
        return "More";
    }


    private List<SearchFieldDataDto> getSearchableFieldsMap() {

        SearchFieldDataDto raProfileFilter = SearchLabelConstants.RA_PROFILE_NAME_FILTER;
        raProfileFilter.setValue(raProfileRepository.findAll().stream().map(RaProfile::getName).collect(Collectors.toList()));

        SearchFieldDataDto groupFilter = SearchLabelConstants.GROUP_NAME_FILTER;
        groupFilter.setValue(groupRepository.findAll().stream().map(CertificateGroup::getName).collect(Collectors.toList()));

        SearchFieldDataDto signatureAlgorithmFilter = SearchLabelConstants.SIGNATURE_ALGORITHM_FILTER;
        signatureAlgorithmFilter.setValue(new ArrayList<>(certificateRepository.findDistinctSignatureAlgorithm()));

        SearchFieldDataDto publicKeyFilter = SearchLabelConstants.PUBLIC_KEY_ALGORITHM_FILTER;
        publicKeyFilter.setValue(new ArrayList<>(certificateRepository.findDistinctPublicKeyAlgorithm()));

        SearchFieldDataDto keySizeFilter = SearchLabelConstants.KEY_SIZE_FILTER;
        keySizeFilter.setValue(new ArrayList<>(certificateRepository.findDistinctKeySize()));

        SearchFieldDataDto keyUsageFilter = SearchLabelConstants.KEY_USAGE_FILTER;
        keyUsageFilter.setValue(serializedListOfStringToListOfObject(certificateRepository.findDistinctKeyUsage()));

        List<SearchFieldDataDto> fields = List.of(
                SearchLabelConstants.COMMON_NAME_FILTER,
                SearchLabelConstants.SERIAL_NUMBER_FILTER,
                SearchLabelConstants.ISSUER_SERIAL_NUMBER_FILTER,
                raProfileFilter,
                groupFilter,
                SearchLabelConstants.OWNER_FILTER,
                SearchLabelConstants.STATUS_FILTER,
                SearchLabelConstants.COMPLIANCE_STATUS_FILTER,
                SearchLabelConstants.ISSUER_COMMON_NAME_FILTER,
                SearchLabelConstants.FINGERPRINT_FILTER,
                signatureAlgorithmFilter,
                SearchLabelConstants.NOT_AFTER_FILTER,
                SearchLabelConstants.NOT_BEFORE_FILTER,
                SearchLabelConstants.SUBJECTDN_FILTER,
                SearchLabelConstants.ISSUERDN_FILTER,
                SearchLabelConstants.META_FILTER,
                SearchLabelConstants.SUBJECT_ALTERNATIVE_NAMES_FILTER,
                SearchLabelConstants.OCSP_VALIDATION_FILTER,
                SearchLabelConstants.CRL_VALIDATION_FILTER,
                SearchLabelConstants.SIGNATURE_VALIDATION_FILTER,
                publicKeyFilter,
                keySizeFilter,
                keyUsageFilter
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

    private CertificateResponseDto getCertificatesWithFilter(SearchRequestDto request, SecurityFilter filter) {
        logger.debug("Certificate search request: {}", request.toString());
        CertificateResponseDto certificateResponseDto = new CertificateResponseDto();
        if (request.getItemsPerPage() == null) {
            request.setItemsPerPage(DEFAULT_PAGE_SIZE);
        }
        if (request.getPageNumber() == null) {
            request.setPageNumber(1);
        }
        if (request.getFilters() == null || request.getFilters().isEmpty()) {
            Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
            Long maxSize = certificateRepository.countUsingSecurityFilter(filter);
            certificateResponseDto.setTotalPages((int) Math.ceil((double) maxSize / request.getItemsPerPage()));
            certificateResponseDto.setTotalItems(maxSize);
            certificateResponseDto.setCertificates(certificateRepository.findUsingSecurityFilter(
                            filter, null,
                            p, (root, cb) -> cb.desc(root.get("created")))
                    .stream()
                    .map(Certificate::mapToDto)
                    .collect(Collectors.toList()
                    )
            );
        } else {
            DynamicSearchInternalResponse dynamicSearchInternalResponse = searchService.dynamicSearchQueryExecutor(request, "Certificate", getSearchableFieldInformation(), searchService.createCriteriaBuilderString(filter, true));
            certificateResponseDto.setItemsPerPage(request.getItemsPerPage());
            certificateResponseDto.setTotalItems(dynamicSearchInternalResponse.getTotalItems());
            certificateResponseDto.setTotalPages(dynamicSearchInternalResponse.getTotalPages());
            certificateResponseDto.setPageNumber(request.getPageNumber());
            certificateResponseDto.setCertificates(((List<Certificate>) dynamicSearchInternalResponse.getResult()).stream().map(Certificate::mapToDto).collect(Collectors.toList()));
        }
        return certificateResponseDto;
    }

    private List<CertificateComplianceResultDto> frameComplianceResult(CertificateComplianceStorageDto storageDto) {
        logger.debug("Framing Compliance Result from stored data: {}", storageDto);
        List<CertificateComplianceResultDto> result = new ArrayList<>();
        List<ComplianceProfileRule> rules = complianceService.getComplianceProfileRuleEntityForIds(storageDto.getNok());
        List<ComplianceRule> rulesWithoutAttributes = complianceService.getComplianceRuleEntityForIds(storageDto.getNok());
        // List<ComplianceRule> naRules = complianceService.getComplianceRuleEntityForIds(storageDto.getNa());
        for (ComplianceProfileRule complianceRule : rules) {
            result.add(getCertificateComplianceResultDto(complianceRule, ComplianceRuleStatus.NOK));
        }
        for (ComplianceRule complianceRule : rulesWithoutAttributes) {
            result.add(getCertificateComplianceResultDto(complianceRule, ComplianceRuleStatus.NOK));
        }
        // NA Rules are not required to be displayed in the UI
        // for (ComplianceRule complianceRule : naRules) {
        //     result.add(getCertificateComplianceResultDto(complianceRule, ComplianceRuleStatus.NA));
        // }
        logger.debug("Compliance Result: {}", result);
        return result;
    }

    private CertificateComplianceResultDto getCertificateComplianceResultDto(ComplianceProfileRule rule, ComplianceRuleStatus status) {
        CertificateComplianceResultDto dto = new CertificateComplianceResultDto();
        dto.setConnectorName(rule.getComplianceRule().getConnector().getName());
        dto.setRuleName(rule.getComplianceRule().getName());
        dto.setRuleDescription(rule.getComplianceRule().getDescription());
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(rule.getAttributes()));
        dto.setStatus(status);
        return dto;
    }

    private CertificateComplianceResultDto getCertificateComplianceResultDto(ComplianceRule rule, ComplianceRuleStatus status) {
        CertificateComplianceResultDto dto = new CertificateComplianceResultDto();
        dto.setConnectorName(rule.getConnector().getName());
        dto.setRuleName(rule.getName());
        dto.setRuleDescription(rule.getDescription());
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(rule.getAttributes()));
        dto.setStatus(status);
        return dto;
    }

    @Async
    // TODO - move to search service
    private void bulkUpdateRaProfileComplianceCheck(List<SearchFilterRequestDto> searchFilter) {
        List<Certificate> certificates = (List<Certificate>) searchService.completeSearchQueryExecutor(searchFilter, "Certificate", getSearchableFieldInformation());
        CertificateComplianceCheckDto dto = new CertificateComplianceCheckDto();
        dto.setCertificateUuids(certificates.stream().map(Certificate::getUuid).map(UUID::toString).collect(Collectors.toList()));
        checkCompliance(dto);
    }

    private void certificateComplianceCheck(Certificate certificate) {
        if (certificate.getRaProfile() != null) {
            try {
                complianceService.checkComplianceOfCertificate(certificate);
            } catch (ConnectorException e) {
                logger.error("Error when checking compliance");
            }
        }
    }


    // TODO: Predicate for future use to construct the conditions, and forward to the data repository

    //    private Predicate createPredicate(List<SearchFilterRequestDto> filters) {
//        BooleanBuilder predicate = new BooleanBuilder();
//
//        Path<Certificate> certificate = Expressions.path(Certificate.class, "certificate");
//        try {
//            Class fieldClass = Certificate.class.getField(filters.get(0).getField().getCode()).getClass();
//            Path<Class> certificateField = Expressions.path(Class.class, certificate, filters.get(0).getField().getCode());
//
//
//            PathBuilder<Certificate> entityPath = new PathBuilder<Certificate>(Certificate.class, "certificate");
//            StringPath sp = entityPath.get(filters.get(0).getField().getCode(), fieldClass.getClass());
//
//            predicate.and(entityPath.get.eq(filters.get(0).getValue()));
//
//            Expressions.predicate(Ops.AND, certificateField, filters.get(0).getValue().toString());
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        }
//        Expressions.predicate(Ops.AND, )
//        filters.get(0).getField().getClass()
//
//        return predicate;
//    }
    private boolean downloadUploadChain(Certificate certificate) {
        List<String> chainCertificates = downloadChainFromAia(certificate);
        List<Certificate> uploadedCertificate = new ArrayList<>();
        if (chainCertificates.isEmpty()) {
            return false;
        }

        for (String cert : chainCertificates) {
            try {
                uploadedCertificate.add(checkCreateCertificate(cert));
            } catch (Exception e) {
                logger.error("Chain already exists");
            }
        }

        if (!uploadedCertificate.isEmpty()) {
            updateIssuer();
            return true;
        } else {
            return false;
        }
    }

    private List<String> downloadChainFromAia(Certificate certificate) {
        List<String> chainCertificates = new ArrayList<>();
        String oldChainUrl = "";
        String chainUrl;
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

    private void updateRaProfile(SecuredUUID uuid, SecuredUUID raProfileUuid) throws NotFoundException {
        Certificate certificate = getCertificateEntity(uuid);
        RaProfile raProfile = raProfileRepository.findByUuid(raProfileUuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, raProfileUuid));
        String originalProfile = "undefined";
        if (certificate.getRaProfile() != null) {
            originalProfile = certificate.getRaProfile().getName();
        }
        certificate.setRaProfile(raProfile);
        certificateRepository.save(certificate);
        try {
            complianceService.checkComplianceOfCertificate(certificate);
        } catch (ConnectorException e) {
            logger.error("Error when checking compliance:", e);
        }
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_RA_PROFILE, CertificateEventStatus.SUCCESS, originalProfile + " -> " + raProfile.getName(), "", certificate);
    }

    private void updateCertificateGroup(SecuredUUID uuid, SecuredUUID groupUuid) throws NotFoundException {
        Certificate certificate = getCertificateEntity(uuid);

        CertificateGroup certificateGroup = groupRepository.findByUuid(groupUuid)
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, groupUuid));
        String originalGroup = "undefined";
        if (certificate.getGroup() != null) {
            originalGroup = certificate.getGroup().getName();
        }
        certificate.setGroup(certificateGroup);
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, originalGroup + " -> " + certificateGroup.getName(), "", certificate);
    }

    private void updateOwner(SecuredUUID uuid, String owner) throws NotFoundException {
        Certificate certificate = getCertificateEntity(uuid);
        String originalOwner = certificate.getOwner();
        if (originalOwner == null || originalOwner.isEmpty()) {
            originalOwner = "undefined";
        }
        certificate.setOwner(owner);
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, originalOwner + " -> " + owner, "", certificate);
    }

    private void bulkUpdateRaProfile(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException {
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        RaProfile raProfile = raProfileRepository.findByUuid(SecuredUUID.fromString(request.getRaProfileUuid()))
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getRaProfileUuid()));
        if (request.getFilters() == null) {
            List<Certificate> batchOperationList = new ArrayList<>();
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = ((CertificateService) AopContext.currentProxy()).getCertificateEntity(SecuredUUID.fromString(certificateUuid));
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_RA_PROFILE, CertificateEventStatus.SUCCESS, certificate.getRaProfile() != null ? certificate.getRaProfile().getName() : "undefined" + " -> " + raProfile.getName(), "", certificate));
                certificate.setRaProfile(raProfile);
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String data = searchService.createCriteriaBuilderString(filter, false);
            if (!data.equals("")) {
                data = "WHERE " + data;
            }
            String profileUpdateQuery = "UPDATE Certificate c SET c.raProfile = " + raProfile.getUuid() + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", data, getSearchableFieldInformation(), true, false).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            certificateRepository.bulkUpdateQuery(profileUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(), CertificateEvent.UPDATE_RA_PROFILE, CertificateEventStatus.SUCCESS, "RA Profile Name: " + raProfile.getName());
            bulkUpdateRaProfileComplianceCheck(request.getFilters());
        }
    }

    private void bulkUpdateCertificateGroup(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException {
        CertificateGroup certificateGroup = groupRepository.findByUuid(SecuredUUID.fromString(request.getGroupUuid()))
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, request.getGroupUuid()));
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null) {
            List<Certificate> batchOperationList = new ArrayList<>();

            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = ((CertificateService) AopContext.currentProxy()).getCertificateEntity(SecuredUUID.fromString(certificateUuid));
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, certificate.getGroup() != null ? certificate.getGroup().getName() : "undefined" + " -> " + certificateGroup.getName(), "", certificate));
                certificate.setGroup(certificateGroup);
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String data = searchService.createCriteriaBuilderString(filter, false);
            if (!data.equals("")) {
                data = "WHERE " + data;
            }
            String groupUpdateQuery = "UPDATE Certificate c SET c.group = " + certificateGroup.getUuid() + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", data, getSearchableFieldInformation(), true, false).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            certificateRepository.bulkUpdateQuery(groupUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(), CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, "Group Name: " + certificateGroup.getName());
        }
    }

    private void bulkUpdateOwner(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException {
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null) {
            List<Certificate> batchOperationList = new ArrayList<>();
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = ((CertificateService) AopContext.currentProxy()).getCertificateEntity(SecuredUUID.fromString(certificateUuid));
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, certificate.getOwner() + " -> " + request.getOwner(), "", certificate));
                certificate.setOwner(request.getOwner());
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String data = searchService.createCriteriaBuilderString(filter, false);
            if (!data.equals("")) {
                data = "WHERE " + data;
            }
            String ownerUpdateQuery = "UPDATE Certificate c SET c.owner = '" + request.getOwner() + "' " + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", data, getSearchableFieldInformation(), true, false).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            certificateRepository.bulkUpdateQuery(ownerUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(), CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, "Owner: " + request.getOwner());
        }

    }
}

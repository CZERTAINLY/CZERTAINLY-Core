package com.czertainly.core.service.impl;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.common.AuthenticationServiceExceptionDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.compliance.ComplianceRuleStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.DynamicSearchInternalResponse;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.search.SearchGroup;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.attribute.CsrAttributes;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.messaging.producers.EventProducer;
import com.czertainly.core.messaging.producers.NotificationProducer;
import com.czertainly.core.model.SearchFieldObject;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.security.exception.AuthenticationServiceException;
import com.czertainly.core.service.*;
import com.czertainly.core.service.v2.ExtendedAttributeService;
import com.czertainly.core.util.*;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
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

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateRequestRepository certificateRequestRepository;

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
    private AttributeContentRepository attributeContentRepository;

    @Autowired
    private AttributeContent2ObjectRepository attributeContent2ObjectRepository;

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

    @Lazy
    @Autowired
    private CryptographicKeyService cryptographicKeyService;

    @Autowired
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private NotificationProducer notificationProducer;

    @Autowired
    private UserManagementApiClient userManagementApiClient;

    @Autowired
    private ExtendedAttributeService extendedAttributeService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.LIST, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.LIST)
    public CertificateResponseDto listCertificates(SecurityFilter filter, SearchRequestDto request) throws ValidationException {
        filter.setParentRefProperty("raProfileUuid");
        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final List<UUID> objectUUIDs = new ArrayList<>();
        if (!request.getFilters().isEmpty()) {
            final List<SearchFieldObject> searchFieldObjects = new ArrayList<>();
            searchFieldObjects.addAll(getSearchFieldObjectForMetadata());
            searchFieldObjects.addAll(getSearchFieldObjectForCustomAttributes());

            final Sql2PredicateConverter.CriteriaQueryDataObject criteriaQueryDataObject = Sql2PredicateConverter.prepareQueryToSearchIntoAttributes(searchFieldObjects, request.getFilters(), entityManager.getCriteriaBuilder(), Resource.CERTIFICATE);
            objectUUIDs.addAll(certificateRepository.findUsingSecurityFilterByCustomCriteriaQuery(filter, criteriaQueryDataObject.getRoot(), criteriaQueryDataObject.getCriteriaQuery(), criteriaQueryDataObject.getPredicate()));
        }

        final BiFunction<Root<Certificate>, CriteriaBuilder, Predicate> additionalWhereClause = (root, cb) -> Sql2PredicateConverter.mapSearchFilter2Predicates(request.getFilters(), cb, root, objectUUIDs);
        final List<CertificateDto> listedKeyDTOs = certificateRepository.findUsingSecurityFilter(filter, additionalWhereClause, p, (root, cb) -> cb.desc(root.get("created")))
                .stream()
                .map(Certificate::mapToListDto)
                .collect(Collectors.toList());
        final Long maxItems = certificateRepository.countUsingSecurityFilter(filter, additionalWhereClause);

        final CertificateResponseDto responseDto = new CertificateResponseDto();
        responseDto.setCertificates(listedKeyDTOs);
        responseDto.setItemsPerPage(request.getItemsPerPage());
        responseDto.setPageNumber(request.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / request.getItemsPerPage()));
        return responseDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DETAIL)
    public CertificateDetailDto getCertificate(SecuredUUID uuid) throws NotFoundException, CertificateException, IOException {
        Certificate entity = getCertificateEntity(uuid);
        CertificateDetailDto dto = entity.mapToDto();
        if (entity.getComplianceResult() != null) {
            dto.setNonCompliantRules(frameComplianceResult(entity.getComplianceResult()));
        } else {
            dto.setComplianceStatus(ComplianceStatus.NA);
        }
        dto.setMetadata(metadataService.getFullMetadataWithNullResource(entity.getUuid(), Resource.CERTIFICATE, List.of(Resource.DISCOVERY)));
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.CERTIFICATE));
        dto.setRelatedCertificates(certificateRepository.findBySourceCertificateUuid(entity.getUuid()).stream().map(Certificate::mapToListDto).collect(Collectors.toList()));
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
    public List<Certificate> getCertificateEntityBySubjectDn(String subjectDn) {
        return certificateRepository.findBySubjectDn(subjectDn);
    }

    @Override
    public List<Certificate> getCertificateEntityByCommonName(String commonName) {
        return certificateRepository.findByCommonName(commonName);
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
    public Certificate getCertificateEntityByIssuerDnAndSerialNumber(String issuerDn, String serialNumber) throws NotFoundException {
        return certificateRepository.findByIssuerDnAndSerialNumber(issuerDn, serialNumber)
                .orElseThrow(() -> new NotFoundException(Certificate.class, issuerDn + " " + serialNumber));
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

        CertificateContent content = (certificate.getCertificateContent() != null && discoveryCertificateRepository.findByCertificateContent(certificate.getCertificateContent()).isEmpty())
                ? certificateContentRepository.findById(certificate.getCertificateContent().getId()).orElse(null)
                : null;
        certificateRepository.delete(certificate);
        if (content != null) {
            certificateContentRepository.delete(content);
        }
        attributeService.deleteAttributeContent(uuid.getValue(), Resource.CERTIFICATE);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.UPDATE)
    public void updateCertificateObjects(SecuredUUID uuid, CertificateUpdateObjectsDto request) throws NotFoundException {
        logger.info("Updating certificate objects: RA {} group {} owner {}", request.getRaProfileUuid(), request.getGroupUuid(), request.getOwnerUuid());
        if (request.getRaProfileUuid() != null) {
            updateRaProfile(uuid, SecuredUUID.fromString(request.getRaProfileUuid()));
        }
        if (request.getGroupUuid() != null) {
            updateCertificateGroup(uuid, SecuredUUID.fromString(request.getGroupUuid()));
        }
        if (request.getOwnerUuid() != null) {
            updateOwner(uuid, request.getOwnerUuid());
        }
    }

    @Async
    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.UPDATE, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.DETAIL)
    public void bulkUpdateCertificateObjects(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException {
        logger.info("Bulk updating certificate objects: RA {} group {} owner {}", request.getRaProfileUuid(), request.getGroupUuid(), request.getOwnerUuid());
        filter.setParentRefProperty("raProfileUuid");
        if (request.getRaProfileUuid() != null) {
            bulkUpdateRaProfile(filter, request);
        }
        if (request.getGroupUuid() != null) {
            bulkUpdateCertificateGroup(filter, request);
        }
        if (request.getOwnerUuid() != null) {
            bulkUpdateOwner(filter, request);
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.DELETE)
    @Async("threadPoolTaskExecutor")
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.DELETE, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.DETAIL)
    public void bulkDeleteCertificate(SecurityFilter filter, RemoveCertificateDto request) throws NotFoundException {
        filter.setParentRefProperty("raProfileUuid");
//        Integer totalItems;
//        BulkOperationResponse bulkOperationResponse = new BulkOperationResponse();
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null || request.getFilters().isEmpty() || (request.getUuids() != null && !request.getUuids().isEmpty())) {
            for (String uuid : request.getUuids()) {
                try {
                    deleteCertificate(SecuredUUID.fromString(uuid));
                } catch (Exception e) {
                    logger.error("Unable to delete the certificate.", e.getMessage());
                }
            }
        } else {
//            List<Certificate> certList = (List<Certificate>) searchService.completeSearchQueryExecutor(request.getFilters(), "Certificate", getSearchableFieldInformation());
//            totalItems = certList.size();

            String joins = "WHERE c.userUuid IS NULL";
            String data = searchService.createCriteriaBuilderString(filter, true);
            if (!data.equals("")) {
                joins = joins + " AND " + data;
            }

            String customQuery = searchService.getQueryDynamicBasedOnFilter(request.getFilters(), "Certificate", getSearchableFieldInformation(), joins, false, false, "");

            List<Certificate> certListDyn = (List<Certificate>) searchService.customQueryExecutor(customQuery);

//            bulkOperationResponse.setFailedItem(Long.valueOf(totalItems - certListDyn.size()));

            for (List<Certificate> certificates : partitionList(certListDyn)) {
                certificateRepository.deleteAll(certificates);
            }
            for (List<CertificateContent> certificateContents : partitionContents(certificateContentRepository.findCertificateContentNotUsed())) {
                certificateContentRepository.deleteAll(certificateContents);
            }
        }
        certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
    }

    @Deprecated
    public List<SearchFieldDataDto> getSearchableFieldInformation() {
        return getSearchableFieldsMap();
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {

        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = new ArrayList<>();

        final List<SearchFieldObject> metadataSearchFieldObject = getSearchFieldObjectForMetadata();
        if (metadataSearchFieldObject.size() > 0) {
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(metadataSearchFieldObject), SearchGroup.META));
        }

        final List<SearchFieldObject> customAttrSearchFieldObject = getSearchFieldObjectForCustomAttributes();
        if (customAttrSearchFieldObject.size() > 0) {
            searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(SearchHelper.prepareSearchForJSON(customAttrSearchFieldObject), SearchGroup.CUSTOM));
        }

        List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(SearchFieldNameEnum.COMMON_NAME),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SERIAL_NUMBER_LABEL),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ISSUER_SERIAL_NUMBER),
                SearchHelper.prepareSearch(SearchFieldNameEnum.RA_PROFILE, raProfileRepository.findAll().stream().map(RaProfile::getName).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.GROUP, groupRepository.findAll().stream().map(Group::getName).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.OWNER),
                SearchHelper.prepareSearch(SearchFieldNameEnum.STATUS, Arrays.stream(CertificateStatus.values()).map(CertificateStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.COMPLIANCE_STATUS, Arrays.stream(ComplianceStatus.values()).map(ComplianceStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ISSUER_COMMON_NAME),
                SearchHelper.prepareSearch(SearchFieldNameEnum.FINGERPRINT),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SIGNATURE_ALGORITHM, new ArrayList<>(certificateRepository.findDistinctSignatureAlgorithm())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.EXPIRES),
                SearchHelper.prepareSearch(SearchFieldNameEnum.NOT_BEFORE),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SUBJECT_DN),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ISSUER_DN),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SUBJECT_ALTERNATIVE),
                SearchHelper.prepareSearch(SearchFieldNameEnum.OCSP_VALIDATION, Arrays.stream((CertificateValidationStatus.values())).map(CertificateValidationStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.CRL_VALIDATION, Arrays.stream((CertificateValidationStatus.values())).map(CertificateValidationStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SIGNATURE_VALIDATION, Arrays.stream((CertificateValidationStatus.values())).map(CertificateValidationStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.PUBLIC_KEY_ALGORITHM, new ArrayList<>(certificateRepository.findDistinctPublicKeyAlgorithm())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.KEY_SIZE, new ArrayList<>(certificateRepository.findDistinctKeySize())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.KEY_USAGE, serializedListOfStringToListOfObject(certificateRepository.findDistinctKeyUsage())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.PRIVATE_KEY)
        );

        fields = fields.stream().collect(Collectors.toList());
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, SearchGroup.PROPERTY));

        logger.debug("Searchable Fields by Groups: {}", searchFieldDataByGroupDtos);
        return searchFieldDataByGroupDtos;

    }

    private List<SearchFieldObject> getSearchFieldObjectForMetadata() {
        return attributeContentRepository.findDistinctAttributeContentNamesByAttrTypeAndObjType(Resource.CERTIFICATE, AttributeType.META);
    }

    private List<SearchFieldObject> getSearchFieldObjectForCustomAttributes() {
        return attributeContentRepository.findDistinctAttributeContentNamesByAttrTypeAndObjType(Resource.CERTIFICATE, AttributeType.CUSTOM);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    //Auth is not required for methods. It is only internally used by other services to update the issuers of the certificate
    public void updateCertificateIssuer(Certificate certificate) throws NotFoundException {
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

                if (issuer.getIssuerSerialNumber() == null) {
                    updateCertificateIssuer(issuer);
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
        if (modal.getKey() == null) {
            UUID keyUuid = cryptographicKeyService.findKeyByFingerprint(modal.getPublicKeyFingerprint());
            if (keyUuid != null) modal.setKeyUuid(keyUuid);
        }
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
    public CertificateDetailDto upload(UploadCertificateRequestDto request)
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
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.CERTIFICATE);
        attributeService.createAttributeContent(entity.getUuid(), request.getCustomAttributes(), Resource.CERTIFICATE);
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

        try {
            UserProfileDto userProfileDto = AuthHelper.getUserProfile();
            entity.setOwner(userProfileDto.getUser().getUsername());
            entity.setOwnerUuid(UUID.fromString(userProfileDto.getUser().getUuid()));
        } catch (Exception e) {
            logger.warn("Unable to set owner to logged user: {}", e.getMessage());
        }

        certificateRepository.save(entity);
        certificateComplianceCheck(entity);
        return entity;
    }

    @Override
    public Certificate checkCreateCertificateWithMeta(String certificate, List<MetadataAttribute> meta, String csr, UUID keyUuid, List<DataAttribute> csrAttributes, List<RequestAttributeDto> signatureAttributes, UUID connectorUuid, UUID sourceCertificateUuid, String issueAttributes) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException {
        X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate);
        String fingerprint = CertificateUtil.getThumbprint(x509Cert);
        if (certificateRepository.findByFingerprint(fingerprint).isPresent()) {
            throw new AlreadyExistException("Certificate already exists with fingerprint " + fingerprint);
        }
        final Certificate entity = createCertificateEntity(x509Cert);
        entity.setKeyUuid(keyUuid);
        entity.setSourceCertificateUuid(sourceCertificateUuid);
        entity.setIssueAttributes(issueAttributes);

        byte[] decodedCSR = Base64.getDecoder().decode(csr);
        final String csrFingerprint = CertificateUtil.getThumbprint(decodedCSR);
        Optional<CertificateRequest> certificateRequestOptional = certificateRequestRepository.findByFingerprint(csrFingerprint);

        if (certificateRequestOptional.isPresent()) {
            entity.setCertificateRequestUuid(certificateRequestOptional.get().getUuid());
        } else {
            CertificateRequest certificateRequest = entity.prepareCertificateRequest(CertificateRequestFormat.PKCS10);
            certificateRequest.setContent(csr);
            certificateRequest.setAttributes(csrAttributes);
            certificateRequest.setSignatureAttributes(signatureAttributes);
            certificateRequest = certificateRequestRepository.save(certificateRequest);
            entity.setCertificateRequestUuid(certificateRequest.getUuid());
        }

        certificateRepository.save(entity);

        metadataService.createMetadataDefinitions(connectorUuid, meta);
        metadataService.createMetadata(connectorUuid, entity.getUuid(), null, null, meta, Resource.CERTIFICATE, null);
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

        final LocationsResponseDto locationsResponseDto = locationService.listLocations(SecurityFilter.create(), new SearchRequestDto());
        final List<String> locations = locationsResponseDto.getLocations()
                .stream()
                .map(LocationDto::getUuid)
                .collect(Collectors.toList());

        final List<LocationDto> locationsCertificate = certificateEntity.getLocations().stream()
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
    // Only Internal method
    public List<Certificate> listCertificatesForRaProfileAndNonNullComplianceStatus(RaProfile raProfile) {
        return certificateRepository.findByRaProfileAndComplianceStatusIsNotNull(raProfile);
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
    public int updateCertificatesStatusScheduled() {
        List<CertificateStatus> skipStatuses = List.of(CertificateStatus.NEW, CertificateStatus.REVOKED, CertificateStatus.EXPIRED);
        long totalCertificates = certificateRepository.countCertificatesToCheckStatus(skipStatuses);
        int maxCertsToValidate = Math.max(100, Math.round(totalCertificates / (float) 24));

        LocalDateTime before = LocalDateTime.now().minusDays(1);

        // process 1/24 of eligible certificates for status update
        final List<Certificate> certificates = certificateRepository.findCertificatesToCheckStatus(
                before,
                skipStatuses,
                PageRequest.of(0, maxCertsToValidate));

        int certificatesUpdated = 0;
        logger.info(MarkerFactory.getMarker("scheduleInfo"), "Scheduled certificate status update. Batch size {}/{} certificates", certificates.size(), totalCertificates);
        for (final Certificate certificate : certificates) {
            String oldStatus = certificate.getStatus().getLabel();
            if (updateCertificateStatusScheduled(certificate)) {
                if (CertificateStatus.REVOKED.equals(certificate.getStatus())
                        || CertificateStatus.EXPIRING.equals(certificate.getStatus())) {

                    try {
                        List<NotificationRecipient> recipient = certificate.getOwnerUuid() != null ? NotificationRecipient.buildUserNotificationRecipient(
                                certificate.getOwnerUuid()) : (certificate.getGroupUuid() != null ? NotificationRecipient.buildGroupNotificationRecipient(
                                certificate.getGroupUuid()) : null);
                        notificationProducer.produceNotificationCertificateStatusChanged(Resource.CERTIFICATE,
                                certificate.getUuid(),
                                recipient,
                                oldStatus,
                                certificate.getStatus().getLabel(),
                                certificate.mapToListDto());
                    } catch (Exception e) {
                        logger.warn("Sending certificate {} notification for change of status {} failed. Error: {}", certificate.getUuid(), certificate.getStatus().getCode(), e.getMessage());
                    }

                    eventProducer.produceEventCertificateMessage(certificate.getUuid(), certificate.getStatus().getCode());
                    logger.info("Certificate {} event was sent with status {}", certificate.getUuid(), certificate.getStatus().getCode());
                }
                ++certificatesUpdated;
            }
        }
        logger.info(MarkerFactory.getMarker("scheduleInfo"), "Certificates status updated for {}/{} certificates", certificatesUpdated, certificates.size());
        return certificatesUpdated;
    }

    private boolean updateCertificateStatusScheduled(Certificate certificate) {
        try {
            updateCertificateIssuer(certificate);
            certValidationService.validate(certificate);
            if (certificate.getRaProfileUuid() != null && certificate.getComplianceStatus() == null) {
                complianceService.checkComplianceOfCertificate(certificate);
            }
        } catch (Exception e) {
            logger.warn(MarkerFactory.getMarker("scheduleInfo"), "Scheduled task was unable to update status of the certificate {}. Certificate {}", e.getMessage(), certificate.toString());
            certificate.setStatusValidationTimestamp(LocalDateTime.now());
            certificateRepository.save(certificate);

            return false;
        }
        return true;
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
            if (!certificate.getStatus().equals(CertificateStatus.NEW)) {
                typeStat.merge(certificate.getCertificateType().getCode(), 1L, Long::sum);
                expiryStat.merge(getExpiryTime(currentTime, certificate.getNotAfter()), 1L, Long::sum);
                bcStat.merge(certificate.getBasicConstraints(), 1L, Long::sum);
            }
            keySizeStat.merge(certificate.getKeySize().toString(), 1L, Long::sum);
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

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.ANY)
    public List<BaseAttribute> getCsrGenerationAttributes() {
        return CsrAttributes.csrAttributes();
    }

    @Override
    public void clearKeyAssociations(UUID keyUuid) {
        List<Certificate> certificates = certificateRepository.findByKeyUuid(keyUuid);
        for (Certificate certificate : certificates) {
            certificate.setKey(null);
            certificate.setKeyUuid(null);
            certificateRepository.save(certificate);
        }
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getCertificateEntity(uuid);
    }

    @Override
    public void updateCertificateKeys(UUID keyUuid, String publicKeyFingerprint) {
        for (Certificate certificate : certificateRepository.findByPublicKeyFingerprint(publicKeyFingerprint)) {
            certificate.setKeyUuid(keyUuid);
            certificateRepository.save(certificate);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.LIST)
    public List<CertificateContentDto> getCertificateContent(List<String> uuids) {
        List<CertificateContentDto> response = new ArrayList<>();
        for (String uuid : uuids) {
            try {
                SecuredUUID securedUUID = SecuredUUID.fromString(uuid);
                permissionEvaluator.certificate(securedUUID);
                Certificate certificate = getCertificateEntity(securedUUID);
                CertificateContentDto dto = new CertificateContentDto();
                dto.setUuid(uuid);
                dto.setCommonName(certificate.getCommonName());
                dto.setSerialNumber(certificate.getSerialNumber());
                dto.setCertificateContent(certificate.getCertificateContent().getContent());
                response.add(dto);
            } catch (Exception e) {
                logger.error("Unable to get the certificate content {}. Exception: ", uuid, e.getMessage());
            }
        }
        return response;
    }

    @Override
    public Certificate createCsr(String csr, List<RequestAttributeDto> signatureAttributes, List<DataAttribute> csrAttributes, UUID keyUuid) throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {

        final JcaPKCS10CertificationRequest jcaObject = CsrUtil.csrStringToJcaObject(csr);
        final Certificate certificate = new Certificate();
        CertificateUtil.prepareCsrObject(certificate, jcaObject);
        certificate.setKeyUuid(keyUuid);
        certificate.setStatus(CertificateStatus.NEW);

        CertificateRequest certificateRequest = certificate.prepareCertificateRequest(CertificateRequestFormat.PKCS10);
        certificateRequest.setContent(csr);
        certificateRequest.setSignatureAttributes(signatureAttributes);
        certificateRequest.setAttributes(csrAttributes);
        certificateRequest = certificateRequestRepository.save(certificateRequest);

        certificate.setCertificateRequest(certificateRequest);
        certificate.setCertificateRequestUuid(certificateRequest.getUuid());
        certificateRepository.save(certificate);
        return certificate;
    }

    @Override
    public Certificate updateCsrToCertificate(UUID uuid, String certificateData, List<MetadataAttribute> meta) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException, NotFoundException {
        X509Certificate x509Cert = CertificateUtil.parseCertificate(certificateData);
        String fingerprint = CertificateUtil.getThumbprint(x509Cert);
        Certificate entity = getCertificateEntity(SecuredUUID.fromUUID(uuid));
        if (certificateRepository.findByFingerprint(fingerprint).isPresent()) {
            throw new AlreadyExistException("Certificate already exists with fingerprint " + fingerprint);
        }
        CertificateUtil.prepareCertificate(entity, x509Cert);
        CertificateContent certificateContent = checkAddCertificateContent(fingerprint, X509ObjectToString.toPem(x509Cert));
        entity.setFingerprint(fingerprint);
        entity.setCertificateContent(certificateContent);
        entity.setCertificateContentId(certificateContent.getId());
        certificateRepository.save(entity);
        metadataService.createMetadataDefinitions(null, meta);
        metadataService.createMetadata(null, entity.getUuid(), null, null, meta, Resource.CERTIFICATE, null);
        certificateComplianceCheck(entity);
        return entity;
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE, action = ResourceAction.LIST, parentResource = Resource.RA_PROFILE, parentAction = ResourceAction.LIST)
    public List<CertificateDto> listScepCaCertificates(SecurityFilter filter, boolean intuneEnabled) {
        filter.setParentRefProperty("raProfileUuid");

        List<Certificate> certificates = certificateRepository.findUsingSecurityFilter(filter,
                (root, cb) -> cb.and(cb.isNotNull(root.get("keyUuid")), cb.or(cb.equal(root.get("status"), CertificateStatus.VALID), cb.equal(root.get("status"), CertificateStatus.EXPIRING))));
        return certificates
                .stream()
                .filter(c -> CertificateUtil.isCertificateScepCaCertAcceptable(c, intuneEnabled))
                .map(Certificate::mapToListDto)
                .collect(Collectors.toList());
    }

    private String getExpiryTime(Date now, Date expiry) {
        long diffInMillies = expiry.getTime() - now.getTime();
        long difference = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
        if (diffInMillies <= 0) {
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


    @Deprecated
    private List<SearchFieldDataDto> getSearchableFieldsMap() {

        final List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(SearchFieldNameEnum.COMMON_NAME),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SERIAL_NUMBER_LABEL),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ISSUER_SERIAL_NUMBER),
                SearchHelper.prepareSearch(SearchFieldNameEnum.RA_PROFILE, raProfileRepository.findAll().stream().map(RaProfile::getName).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.GROUP, groupRepository.findAll().stream().map(Group::getName).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.OWNER),
                SearchHelper.prepareSearch(SearchFieldNameEnum.STATUS, Arrays.stream(CertificateStatus.values()).map(CertificateStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.COMPLIANCE_STATUS, Arrays.stream(ComplianceStatus.values()).map(ComplianceStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ISSUER_COMMON_NAME),
                SearchHelper.prepareSearch(SearchFieldNameEnum.FINGERPRINT),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SIGNATURE_ALGORITHM, new ArrayList<>(certificateRepository.findDistinctSignatureAlgorithm())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.EXPIRES),
                SearchHelper.prepareSearch(SearchFieldNameEnum.NOT_BEFORE),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SUBJECT_DN),
                SearchHelper.prepareSearch(SearchFieldNameEnum.ISSUER_DN),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SUBJECT_ALTERNATIVE),
                SearchHelper.prepareSearch(SearchFieldNameEnum.OCSP_VALIDATION, Arrays.stream((CertificateValidationStatus.values())).map(CertificateValidationStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.CRL_VALIDATION, Arrays.stream((CertificateValidationStatus.values())).map(CertificateValidationStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.SIGNATURE_VALIDATION, Arrays.stream((CertificateValidationStatus.values())).map(CertificateValidationStatus::getCode).collect(Collectors.toList())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.PUBLIC_KEY_ALGORITHM, new ArrayList<>(certificateRepository.findDistinctPublicKeyAlgorithm())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.KEY_SIZE, new ArrayList<>(certificateRepository.findDistinctKeySize())),
                SearchHelper.prepareSearch(SearchFieldNameEnum.KEY_USAGE, serializedListOfStringToListOfObject(certificateRepository.findDistinctKeyUsage()))
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

    @Deprecated
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
                    .map(Certificate::mapToListDto)
                    .collect(Collectors.toList()
                    )
            );
            certificateResponseDto.setItemsPerPage(request.getItemsPerPage());
            certificateResponseDto.setPageNumber(request.getPageNumber());
        } else {
            DynamicSearchInternalResponse dynamicSearchInternalResponse = searchService.dynamicSearchQueryExecutor(request, "Certificate", getSearchableFieldInformation(), searchService.createCriteriaBuilderString(filter, true));
            certificateResponseDto.setItemsPerPage(request.getItemsPerPage());
            certificateResponseDto.setTotalItems(dynamicSearchInternalResponse.getTotalItems());
            certificateResponseDto.setTotalPages(dynamicSearchInternalResponse.getTotalPages());
            certificateResponseDto.setPageNumber(request.getPageNumber());
            certificateResponseDto.setCertificates(((List<Certificate>) dynamicSearchInternalResponse.getResult()).stream().map(Certificate::mapToListDto).collect(Collectors.toList()));
        }
        return certificateResponseDto;
    }

    private List<CertificateComplianceResultDto> frameComplianceResult(CertificateComplianceStorageDto storageDto) {
        logger.debug("Framing Compliance Result from stored data: {}", storageDto);
        List<CertificateComplianceResultDto> result = new ArrayList<>();
        List<ComplianceProfileRule> rules = complianceService.getComplianceProfileRuleEntityForIds(storageDto.getNok());
        List<ComplianceRule> rulesWithoutAttributes = complianceService.getComplianceRuleEntityForIds(storageDto.getNok());
        for (ComplianceProfileRule complianceRule : rules) {
            result.add(getCertificateComplianceResultDto(complianceRule, ComplianceRuleStatus.NOK));
        }
        for (ComplianceRule complianceRule : rulesWithoutAttributes) {
            result.add(getCertificateComplianceResultDto(complianceRule, ComplianceRuleStatus.NOK));
        }
        logger.debug("Compliance Result: {}", result);
        return result;
    }

    private CertificateComplianceResultDto getCertificateComplianceResultDto(ComplianceProfileRule rule, ComplianceRuleStatus status) {
        CertificateComplianceResultDto dto = new CertificateComplianceResultDto();
        dto.setConnectorName(rule.getComplianceRule().getConnector().getName());
        dto.setRuleName(rule.getComplianceRule().getName());
        dto.setRuleDescription(rule.getComplianceRule().getDescription());
        List<DataAttribute> attributes = AttributeDefinitionUtils.mergeAttributes(rule.getComplianceRule().getAttributes(), rule.getAttributes());
        dto.setAttributes(AttributeDefinitionUtils.getResponseAttributes(attributes));
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
            try {
                updateCertificateIssuer(certificate);
            } catch (Exception e) {
                logger.warn("Unable to update the issuer of the certificate {}", certificate.getSerialNumber());
            }
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

        Group group = groupRepository.findByUuid(groupUuid)
                .orElseThrow(() -> new NotFoundException(Group.class, groupUuid));
        String originalGroup = "undefined";
        if (certificate.getGroup() != null) {
            originalGroup = certificate.getGroup().getName();
        }
        certificate.setGroup(group);
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, originalGroup + " -> " + group.getName(), "", certificate);
    }

    private void updateOwner(SecuredUUID uuid, String ownerUuid) throws NotFoundException {
        Certificate certificate = getCertificateEntity(uuid);

        // if there is no change, do not update and save request to Auth service
        if(certificate.getOwnerUuid() != null && certificate.getOwnerUuid().toString().equals(ownerUuid)) return;

        String originalOwner = certificate.getOwner();
        if (originalOwner == null || originalOwner.isEmpty()) {
            originalOwner = "undefined";
        }
        UserDetailDto userDetail = userManagementApiClient.getUserDetail(ownerUuid);
        certificate.setOwner(userDetail.getUsername());
        certificate.setOwnerUuid(UUID.fromString(userDetail.getUuid()));
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, originalOwner + " -> " + userDetail.getUsername(), "", certificate);
    }

    private void bulkUpdateRaProfile(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException {
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        RaProfile raProfile = raProfileRepository.findByUuid(SecuredUUID.fromString(request.getRaProfileUuid()))
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getRaProfileUuid()));
        if (request.getFilters() == null || request.getFilters().isEmpty() || (request.getCertificateUuids() != null && !request.getCertificateUuids().isEmpty())) {
            List<Certificate> batchOperationList = new ArrayList<>();
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = getCertificateEntity(SecuredUUID.fromString(certificateUuid));
                permissionEvaluator.certificate(certificate.getSecuredUuid());
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
        Group group = groupRepository.findByUuid(SecuredUUID.fromString(request.getGroupUuid()))
                .orElseThrow(() -> new NotFoundException(Group.class, request.getGroupUuid()));
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null || request.getFilters().isEmpty() || (request.getCertificateUuids() != null && !request.getCertificateUuids().isEmpty())) {
            List<Certificate> batchOperationList = new ArrayList<>();

            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = getCertificateEntity(SecuredUUID.fromString(certificateUuid));
                permissionEvaluator.certificate(certificate.getSecuredUuid());
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, certificate.getGroup() != null ? certificate.getGroup().getName() : "undefined" + " -> " + group.getName(), "", certificate));
                certificate.setGroup(group);
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String data = searchService.createCriteriaBuilderString(filter, false);
            if (!data.equals("")) {
                data = "WHERE " + data;
            }
            String groupUpdateQuery = "UPDATE Certificate c SET c.group = " + group.getUuid() + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", data, getSearchableFieldInformation(), true, false).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            certificateRepository.bulkUpdateQuery(groupUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(), CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, "Group Name: " + group.getName());
        }
    }

    private void bulkUpdateOwner(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException {
        UserDetailDto userDetail = userManagementApiClient.getUserDetail(request.getOwnerUuid());
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null || request.getFilters().isEmpty() || (request.getCertificateUuids() != null && !request.getCertificateUuids().isEmpty())) {
            List<Certificate> batchOperationList = new ArrayList<>();
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = getCertificateEntity(SecuredUUID.fromString(certificateUuid));
                permissionEvaluator.certificate(certificate.getSecuredUuid());
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, certificate.getOwner() + " -> " + request.getOwnerUuid(), "", certificate));
                certificate.setOwner(userDetail.getUsername());
                certificate.setOwnerUuid(UUID.fromString(userDetail.getUuid()));
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String data = searchService.createCriteriaBuilderString(filter, false);
            if (!data.equals("")) {
                data = "WHERE " + data;
            }
            String ownerUpdateQuery = "UPDATE Certificate c SET c.owner = '" + userDetail.getUsername() + "',c.owner_uuid = '" + UUID.fromString(userDetail.getUuid()) + "' " + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", data, getSearchableFieldInformation(), true, false).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            certificateRepository.bulkUpdateQuery(ownerUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(), CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, "Owner: " + userDetail.getUsername());
        }
    }

    private List<List<Certificate>> partitionList(List<Certificate> fullList) {
        List<List<Certificate>> certificates = new ArrayList<>();

        for (int i = 0; i < fullList.size(); i += DELETE_BATCH_SIZE) {
            certificates.add(fullList.subList(i, Math.min(i + DELETE_BATCH_SIZE, fullList.size())));
        }
        return certificates;
    }

    private List<List<CertificateContent>> partitionContents(List<CertificateContent> fullList) {
        List<List<CertificateContent>> certificates = new ArrayList<>();

        for (int i = 0; i < fullList.size(); i += DELETE_BATCH_SIZE) {
            certificates.add(fullList.subList(i, Math.min(i + DELETE_BATCH_SIZE, fullList.size())));
        }
        return certificates;
    }
}

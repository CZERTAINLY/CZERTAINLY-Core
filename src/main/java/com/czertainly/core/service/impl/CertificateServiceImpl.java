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
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.search.DynamicSearchInternalResponse;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.search.SearchLabelConstants;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.SearchService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.czertainly.core.util.X509ObjectToString;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Integer DEFAULT_PAGE_SIZE = 10;
    private static final Integer DELETE_BATCH_SIZE = 1000;

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

    @Lazy
    @Autowired
    private CertValidationService certValidationService;

    @Autowired
    private CertificateEventHistoryService certificateEventHistoryService;

    @Autowired
    private SearchService searchService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.REQUEST)
    public CertificateResponseDto listCertificates(SearchRequestDto request) throws ValidationException {
        return getCertificatesWithFilter(request);

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void removeCertificate(String uuid) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));

        List<ValidationError> errors = new ArrayList<>();

        for (Client client : clientRepository.findByCertificate(certificate)) {
            errors.add(ValidationError.create("Certificate has Client " + client.getName() + " associated to it"));
            certificateEventHistoryService.addEventHistory(CertificateEvent.DELETE, CertificateEventStatus.FAILED, "Associated to Client " + client.getName(), "", certificate);
        }

        for (Admin admin : adminRepository.findByCertificate(certificate)) {
            errors.add(ValidationError.create("Certificate has Admin " + admin.getName() + " associated to it"));
            certificateEventHistoryService.addEventHistory(CertificateEvent.DELETE, CertificateEventStatus.FAILED, "Associated to Admin  " + admin.getName(), "", certificate);
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
        String originalProfile = "undefined";
        if (certificate.getRaProfile() != null) {
            originalProfile = certificate.getRaProfile().getName();
        }
        certificate.setRaProfile(raProfile);
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_RA_PROFILE, CertificateEventStatus.SUCCESS, originalProfile + " -> " + raProfile.getName(), "", certificate);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateCertificateGroup(String uuid, CertificateUpdateGroupDto request) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));

        CertificateGroup certificateGroup = groupRepository.findByUuid(request.getGroupUuid())
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, request.getGroupUuid()));
        String originalGroup = "undefined";
        if (certificate.getOwner() != null) {
            originalGroup = certificate.getGroup().getName();
        }
        certificate.setGroup(certificateGroup);
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, originalGroup + " -> " + certificateGroup.getName(), "", certificate);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateEntity(String uuid, CertificateUpdateEntityDto request) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        CertificateEntity certificateEntity = entityRepository.findByUuid(request.getEntityUuid())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getEntityUuid()));
        String originalEntity = "undefined";
        if (certificate.getEntity() != null) {
            originalEntity = certificate.getEntity().getName();
        }
        certificate.setEntity(certificateEntity);
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_ENTITY, CertificateEventStatus.SUCCESS, originalEntity + " -> " + certificateEntity.getName(), "", certificate);

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void updateOwner(String uuid, CertificateOwnerRequestDto request) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
        String originalOwner = certificate.getOwner();
        if (originalOwner == null || originalOwner.isEmpty()) {
            originalOwner = "undefined";
        }
        certificate.setOwner(request.getOwner());
        certificateRepository.save(certificate);
        certificateEventHistoryService.addEventHistory(CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, originalOwner + " -> " + request.getOwner(), "", certificate);

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateRaProfile(MultipleRAProfileUpdateDto request) throws NotFoundException {
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        RaProfile raProfile = raProfileRepository.findByUuid(request.getUuid())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getUuid()));
        if (request.getFilters() == null) {
            List<Certificate> batchOperationList = new ArrayList<>();
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_RA_PROFILE, CertificateEventStatus.SUCCESS, certificate.getRaProfile() != null ? certificate.getRaProfile().getName() : "undefined" + " -> " + raProfile.getName(), "", certificate));
                certificate.setRaProfile(raProfile);
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String profileUpdateQuery = "UPDATE certificate c SET ra_profile_id = " + raProfile.getId() + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", "", getSearchableFieldInformation(), true, true).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            searchService.nativeQueryExecutor(profileUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(),CertificateEvent.UPDATE_RA_PROFILE, CertificateEventStatus.SUCCESS, "RA Profile Name: " + raProfile.getName());

        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateCertificateGroup(MultipleGroupUpdateDto request) throws NotFoundException {
        CertificateGroup certificateGroup = groupRepository.findByUuid(request.getUuid())
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, request.getUuid()));
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null) {
            List<Certificate> batchOperationList = new ArrayList<>();

            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, certificate.getGroup() != null ? certificate.getGroup().getName() : "undefined" + " -> " + certificateGroup.getName(), "", certificate));
                certificate.setGroup(certificateGroup);
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String groupUpdateQuery = "UPDATE certificate c SET group_id = " + certificateGroup.getId() + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", "", getSearchableFieldInformation(), true, true).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            searchService.nativeQueryExecutor(groupUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(),CertificateEvent.UPDATE_GROUP, CertificateEventStatus.SUCCESS, "Group Name: " + certificateGroup.getName());
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateEntity(MultipleEntityUpdateDto request) throws NotFoundException {
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        CertificateEntity certificateEntity = entityRepository.findByUuid(request.getUuid())
                .orElseThrow(() -> new NotFoundException(RaProfile.class, request.getUuid()));
        if (request.getFilters() == null) {
            List<Certificate> batchOperationList = new ArrayList<>();
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_ENTITY, CertificateEventStatus.SUCCESS, certificate.getEntity() != null ? certificate.getEntity().getName() : "undefined" + " -> " + certificateEntity.getName(), "", certificate));
                certificate.setEntity(certificateEntity);
                batchOperationList.add(certificate);
                certificateRepository.saveAll(batchOperationList);
                certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
            }
        } else {
            String entityUpdateQuery = "UPDATE certificate c SET entity_id = " + certificateEntity.getId() + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", "", getSearchableFieldInformation(), true, true).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            searchService.nativeQueryExecutor(entityUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(),CertificateEvent.UPDATE_ENTITY, CertificateEventStatus.SUCCESS, "Entity Name: " + certificateEntity.getName());
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CHANGE)
    public void bulkUpdateOwner(CertificateOwnerBulkUpdateDto request) throws NotFoundException {
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null) {
            List<Certificate> batchOperationList = new ArrayList<>();
            for (String certificateUuid : request.getCertificateUuids()) {
                Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, certificate.getOwner() + " -> " + request.getOwner(), "", certificate));
                certificate.setOwner(request.getOwner());
                batchOperationList.add(certificate);
            }
            certificateRepository.saveAll(batchOperationList);
            certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        } else {
            String ownerUpdateQuery = "UPDATE certificate c SET owner = '" + request.getOwner() + "' " + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", "", getSearchableFieldInformation(), true, true).replace("GROUP BY c.id ORDER BY c.id DESC", "");
            searchService.nativeQueryExecutor(ownerUpdateQuery);
            certificateEventHistoryService.addEventHistoryForRequest(request.getFilters(), "Certificate", getSearchableFieldInformation(),CertificateEvent.UPDATE_OWNER, CertificateEventStatus.SUCCESS, "Owner: " + request.getOwner());
        }

    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.DELETE)
    public BulkOperationResponse bulkRemoveCertificate(RemoveCertificateDto request) throws NotFoundException {
        List<String> failedDeleteCerts = new ArrayList<>();
        Integer totalItems;
        BulkOperationResponse bulkOperationResponse = new BulkOperationResponse();
        List<CertificateEventHistory> batchHistoryOperationList = new ArrayList<>();
        if (request.getFilters() == null) {
            for (String uuid : request.getUuids()) {
                Certificate certificate = certificateRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(Certificate.class, uuid));
                if (!adminRepository.findByCertificate(certificate).isEmpty()) {
                    logger.warn("Certificate tagged as admin. Unable to delete certificate with common name {}", certificate.getCommonName());
                    batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.DELETE, CertificateEventStatus.FAILED, "Associated to Client ", "", certificate));
                    failedDeleteCerts.add(certificate.getUuid());
                    continue;
                }

                if (!clientRepository.findByCertificate(certificate).isEmpty()) {
                    logger.warn("Certificate tagged as client. Unable to delete certificate with common name {}", certificate.getCommonName());
                    batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.DELETE, CertificateEventStatus.FAILED, "Associated to Admin ", "", certificate));
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
            List<Certificate> certList = (List<Certificate>) searchService.completeSearchQueryExecutor(request.getFilters(), "Certificate", getSearchableFieldInformation());
            totalItems = certList.size();

            String joins = " LEFT JOIN Admin t1 ON c.id = t1.id  LEFT JOIN Client t2 ON c.id = t2.id WHERE t1.id IS NULL and t2.id is NULL";
            String clientJoins = " LEFT JOIN Client t1 ON c.id = t1.id LEFT JOIN Admin t2 ON c.id = t2.id WHERE t1.id IS NOT NULL AND t2.id IS NOT NULL";

            String customQuery = searchService.getQueryDynamicBasedOnFilter(request.getFilters(), "Certificate", getSearchableFieldInformation(), joins, false, false);
            String clientCustomQuery = searchService.getQueryDynamicBasedOnFilter(request.getFilters(), "Certificate", getSearchableFieldInformation(), clientJoins, false, false);

            List<Certificate> clientUsedCertificates = (List<Certificate>) searchService.customQueryExecutor(clientCustomQuery);
            List<Certificate> certListDyn = (List<Certificate>) searchService.customQueryExecutor(customQuery);

            bulkOperationResponse.setFailedItem(Long.valueOf(totalItems - certListDyn.size()));

            for (Certificate certificate : clientUsedCertificates) {
                batchHistoryOperationList.add(certificateEventHistoryService.getEventHistory(CertificateEvent.DELETE, CertificateEventStatus.FAILED, "Associated to Admin / Client", "", certificate));
            }

            if (certList.size() != certListDyn.size()) {
                bulkOperationResponse.setStatus(BulkOperationStatus.PARTIAL);
                bulkOperationResponse.setMessage("Failed to remove some of the Certificates");
            } else if (failedDeleteCerts.size() == totalItems) {
                bulkOperationResponse.setStatus(BulkOperationStatus.FAILED);
                bulkOperationResponse.setMessage("Failed to delete the Certificates. None of the Certificates were removed");
                return bulkOperationResponse;
            } else {
                bulkOperationResponse.setStatus(BulkOperationStatus.SUCCESS);
                bulkOperationResponse.setMessage("All Certificates are deleted");
            }
            List<Long> ifs = certListDyn.stream().map(Certificate::getId).collect(Collectors.toList());
            for(Certificate certificate : certListDyn) {
                certificateRepository.delete(certificate);
            }
//            searchService.nativeQueryExecutor("DELETE FROM certificate d WHERE d.id IN ( SELECT c.id FROM certificate c LEFT JOIN admin t1 ON c.id = t1.id  LEFT JOIN client t2 ON c.id = t2.id WHERE " + searchService.getCompleteSearchQuery(request.getFilters(), "certificate", " t1.id IS NULL and t2.id is NULL ", getSearchableFieldInformation(), true, true) + ")");
        }
        searchService.asyncNativeQueryExecutor("delete from certificate_content o where o.id in (SELECT c.id FROM certificate_content c LEFT JOIN certificate t1 ON c.id= t1.certificate_content_id LEFT JOIN discovery_certificate t2 ON c.id = t2.certificate_content_id WHERE t1.id IS NULL AND t2.id IS null)");
        certificateEventHistoryService.asyncSaveAllInBatch(batchHistoryOperationList);
        return bulkOperationResponse;
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
//    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CERTIFICATE, operation = OperationType.CREATE)
    public CertificateDto upload(UploadCertificateRequestDto request)
            throws AlreadyExistException, CertificateException {
        X509Certificate certificate = CertificateUtil.parseCertificate(request.getCertificate());
        String certificateSerialNumber = certificate.getSerialNumber().toString(16);
        if (!certificateRepository.findBySerialNumberIgnoreCase(certificateSerialNumber).isEmpty()) {
            throw new AlreadyExistException("Certificate already exists with serial number " + certificateSerialNumber);
        }
        Certificate entity = createCertificateEntity(certificate);
        certificateRepository.save(entity);
//        updateIssuer();
//        try {
//            certValidationService.validate(entity);
//        } catch (Exception e) {
//            logger.warn("Unable to validate the uploaded certificate, {}", e.getMessage());
//        }
//        certificateEventHistoryService.addEventHistory(CertificateEvent.UPLOAD, CertificateEventStatus.SUCCESS, "Certificate uploaded", "", entity);
        return entity.mapToDto();
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

        SearchFieldDataDto raProfileFilter = SearchLabelConstants.RA_PROFILE_NAME_FILTER;
        raProfileFilter.setValue(raProfileRepository.findAll().stream().map(RaProfile::getName).collect(Collectors.toList()));

        SearchFieldDataDto entityFilter = SearchLabelConstants.ENTITY_NAME_FILTER;
        entityFilter.setValue(entityRepository.findAll().stream().map(CertificateEntity::getName).collect(Collectors.toList()));

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
                entityFilter,
                groupFilter,
                SearchLabelConstants.OWNER_FILTER,
                SearchLabelConstants.STATUS_FILTER,
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

    private CertificateResponseDto getCertificatesWithFilter(SearchRequestDto request) {
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
            certificateResponseDto.setTotalPages((int) Math.ceil((double) certificateRepository.count() / request.getItemsPerPage()));
            certificateResponseDto.setTotalItems(certificateRepository.count());
            certificateResponseDto.setCertificates(certificateRepository.findAllByOrderByIdDesc(p).stream().map(Certificate::mapToDto).collect(Collectors.toList()));
        } else {
            DynamicSearchInternalResponse dynamicSearchInternalResponse = searchService.dynamicSearchQueryExecutor(request, "Certificate", getSearchableFieldInformation());
            certificateResponseDto.setItemsPerPage(request.getItemsPerPage());
            certificateResponseDto.setTotalItems(dynamicSearchInternalResponse.getTotalItems());
            certificateResponseDto.setTotalPages(dynamicSearchInternalResponse.getTotalPages());
            certificateResponseDto.setPageNumber(request.getPageNumber());
            certificateResponseDto.setCertificates(((List<Certificate>) dynamicSearchInternalResponse.getResult()).stream().map(Certificate::mapToDto).collect(Collectors.toList()));
        }
        return certificateResponseDto;
    }

    private String getCurrentUsername() {
        return ((UserDetails)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();
    }
}
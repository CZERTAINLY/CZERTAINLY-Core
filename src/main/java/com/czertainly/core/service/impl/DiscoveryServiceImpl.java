package com.czertainly.core.service.impl;

import com.czertainly.api.clients.DiscoveryApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.connector.discovery.DiscoveryDataRequestDto;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderCertificateDataDto;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderDto;
import com.czertainly.api.model.connector.discovery.DiscoveryRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceImpl.class);
    private static final Integer MAXIMUM_CERTIFICATES_PER_PAGE = 100;
    private static final Integer SLEEP_TIME = 5 * 1000; // Seconds * Milliseconds - Retry of discovery for every 5 Seconds
    private static final Long MAXIMUM_WAIT_TIME = (long) (6 * 60 * 60); // Hours * Minutes * Seconds
    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private DiscoveryApiClient discoveryApiClient;
    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateEventHistoryService certificateEventHistoryService;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private DiscoveryCertificateRepository discoveryCertificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private CertValidationService certValidationService;
    @Autowired
    private MetadataService metadataService;
    @Autowired
    private AttributeService attributeService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public List<DiscoveryHistoryDto> listDiscoveries(SecurityFilter filter) {
        return discoveryRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(DiscoveryHistory::mapToListDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public DiscoveryHistoryDetailDto getDiscovery(SecuredUUID uuid) throws NotFoundException {
        DiscoveryHistory discoveryHistory = getDiscoveryEntity(uuid);
        DiscoveryHistoryDetailDto dto = discoveryHistory.mapToDto();
        dto.setMetadata(metadataService.getFullMetadata(discoveryHistory.getUuid(), Resource.DISCOVERY, null, null));
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.DISCOVERY));
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public DiscoveryCertificateResponseDto getDiscoveryCertificates(SecuredUUID uuid,
                                                                    Boolean newlyDiscovered,
                                                                    int itemsPerPage,
                                                                    int pageNumber) throws NotFoundException {
        DiscoveryHistory discoveryHistory = getDiscoveryEntity(uuid);
        // Page number for the user always starts from 1. But for JPA, page number starts from 0
        Pageable p = PageRequest.of(pageNumber > 1 ? pageNumber - 1 : 0, itemsPerPage);
        List<DiscoveryCertificate> certificates;
        Long maxItems;
        if (newlyDiscovered == null) {
            certificates = discoveryCertificateRepository.findByDiscovery(discoveryHistory, p);
            maxItems = discoveryCertificateRepository.countByDiscovery(discoveryHistory);
        } else {
            certificates = discoveryCertificateRepository.findByDiscoveryAndNewlyDiscovered(discoveryHistory, newlyDiscovered, p);
            maxItems = discoveryCertificateRepository.countByDiscoveryAndNewlyDiscovered(discoveryHistory, newlyDiscovered);
        }

        final DiscoveryCertificateResponseDto responseDto = new DiscoveryCertificateResponseDto();
        responseDto.setCertificates(certificates.stream().map(DiscoveryCertificate::mapToDto).collect(Collectors.toList()));
        responseDto.setItemsPerPage(itemsPerPage);
        responseDto.setPageNumber(pageNumber);
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / itemsPerPage));
        return responseDto;
    }

    public DiscoveryHistory getDiscoveryEntity(SecuredUUID uuid) throws NotFoundException {
        return discoveryRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(DiscoveryHistory.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DELETE)
    public void deleteDiscovery(SecuredUUID uuid) throws NotFoundException {
        DiscoveryHistory discovery = discoveryRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(DiscoveryHistory.class, uuid));
        for (DiscoveryCertificate cert : discoveryCertificateRepository.findByDiscovery(discovery)) {
            try {
                discoveryCertificateRepository.delete(cert);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            if (certificateRepository.findByCertificateContent(cert.getCertificateContent()) == null) {
                CertificateContent content = certificateContentRepository.findById(cert.getCertificateContent().getId())
                        .orElse(null);
                if (content != null) {
                    try {
                        certificateContentRepository.delete(content);
                    } catch (Exception e) {
                        logger.warn("Failed to delete the certificate.");
                        logger.warn(e.getMessage());
                    }
                }
            }
        }
        try {
            String referenceUuid = discovery.getDiscoveryConnectorReference();
            attributeService.deleteAttributeContent(discovery.getUuid(), Resource.DISCOVERY);
            discoveryRepository.delete(discovery);
            if (referenceUuid != null && !referenceUuid.isEmpty()) {
                Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromUUID(discovery.getConnectorUuid()));
                discoveryApiClient.removeDiscovery(connector.mapToDto(), referenceUuid);
            }
        } catch (ConnectorException e) {
            logger.warn("Failed to delete discovery in the connector. But core history is deleted");
            logger.warn(e.getMessage());
        } catch (Exception e) {
            logger.warn(e.getMessage());
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DELETE)
    public void bulkRemoveDiscovery(List<SecuredUUID> discoveryUuids) throws NotFoundException {
        for (SecuredUUID uuid : discoveryUuids) {
            deleteDiscovery(uuid);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public Long statisticsDiscoveryCount(SecurityFilter filter) {
        return discoveryRepository.countUsingSecurityFilter(filter);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.CREATE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void createDiscovery(DiscoveryDto request, DiscoveryHistory modal)
            throws ConnectorException {

        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(
                SecuredUUID.fromString(request.getConnectorUuid()),
                FunctionGroupCode.DISCOVERY_PROVIDER,
                request.getAttributes(),
                request.getKind());

        try {
            DiscoveryRequestDto dtoRequest = new DiscoveryRequestDto();
            dtoRequest.setName(request.getName());
            dtoRequest.setKind(request.getKind());

            // Load complete credential data
            credentialService.loadFullCredentialData(attributes);
            dtoRequest.setAttributes(AttributeDefinitionUtils.getClientAttributes(attributes));

            Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));
            DiscoveryProviderDto response = discoveryApiClient.discoverCertificates(connector.mapToDto(), dtoRequest);

            modal.setDiscoveryConnectorReference(response.getUuid());
            discoveryRepository.save(modal);

            DiscoveryDataRequestDto getRequest = new DiscoveryDataRequestDto();
            getRequest.setName(response.getName());
            getRequest.setKind(request.getKind());
            getRequest.setStartIndex(0);
            getRequest.setEndIndex(MAXIMUM_CERTIFICATES_PER_PAGE);

            Boolean waitForCompletion = checkForCompletion(response);
            boolean isReachedMaxTime = false;
            int oldCertificateCount = 0;
            while (waitForCompletion) {
                if (modal.getDiscoveryConnectorReference() == null) {
                    return;
                }
                logger.debug("Waiting {}ms.", SLEEP_TIME);
                Thread.sleep(SLEEP_TIME);

                response = discoveryApiClient.getDiscoveryData(connector.mapToDto(), getRequest, response.getUuid());

                if ((modal.getStartTime().getTime() - new Date().getTime()) / 1000 > MAXIMUM_WAIT_TIME
                        && !isReachedMaxTime && oldCertificateCount == response.getTotalCertificatesDiscovered()) {
                    isReachedMaxTime = true;
                    modal.setStatus(DiscoveryStatus.WARNING);
                    modal.setMessage(
                            "Discovery exceeded maximum time of " + MAXIMUM_WAIT_TIME / (60 * 60) + " hours. There are no changes in number of certificates discovered. Please abort the discovery if the provider is stuck in IN_PROGRESS");
                }
                discoveryRepository.save(modal);
                oldCertificateCount = response.getTotalCertificatesDiscovered();
                waitForCompletion = checkForCompletion(response);
            }

            Integer currentTotal = 0;
            Set<DiscoveryProviderCertificateDataDto> certificatesDiscovered = new HashSet<>();
            while (currentTotal < response.getTotalCertificatesDiscovered()) {
                getRequest.setStartIndex(currentTotal);
                getRequest.setEndIndex(currentTotal + MAXIMUM_CERTIFICATES_PER_PAGE);
                response = discoveryApiClient.getDiscoveryData(connector.mapToDto(), getRequest, response.getUuid());
                if (response.getCertificateData().size() > MAXIMUM_CERTIFICATES_PER_PAGE) {
                    response.setStatus(DiscoveryStatus.FAILED);
                    updateDiscovery(modal, response);
                    logger.error("Too many content in response. Maximum processable is " + MAXIMUM_CERTIFICATES_PER_PAGE);
                    throw new InterruptedException(
                            "Too many content in response to process. Maximum processable is " + MAXIMUM_CERTIFICATES_PER_PAGE);
                }
                currentTotal += MAXIMUM_CERTIFICATES_PER_PAGE;
                certificatesDiscovered.addAll(response.getCertificateData());
            }

            updateDiscovery(modal, response);
            List<Certificate> certificates = updateCertificates(certificatesDiscovered, modal);
            certValidationService.validateCertificates(certificates);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            modal.setStatus(DiscoveryStatus.FAILED);
            modal.setMessage(e.getMessage());
            discoveryRepository.save(modal);
            logger.error(e.getMessage());
        } catch (Exception e) {

            modal.setStatus(DiscoveryStatus.FAILED);
            modal.setMessage(e.getMessage());
            discoveryRepository.save(modal);
            logger.error(e.getMessage());
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.CREATE)
    public DiscoveryHistory createDiscoveryModal(DiscoveryDto request) throws AlreadyExistException, ConnectorException {
        if (discoveryRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(DiscoveryHistory.class, request.getName());
        }
        if (request.getConnectorUuid() == null) {
            throw new ValidationException(ValidationError.create("Connector UUID is empty"));
        }
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));

        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(
                SecuredUUID.fromString(request.getConnectorUuid()),
                FunctionGroupCode.DISCOVERY_PROVIDER,
                request.getAttributes(),
                request.getKind());
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.DISCOVERY);
        DiscoveryHistory modal = new DiscoveryHistory();
        modal.setName(request.getName());
        modal.setConnectorName(connector.getName());
        modal.setStartTime(new Date());
        modal.setStatus(DiscoveryStatus.IN_PROGRESS);
        modal.setConnectorUuid(connector.getUuid().toString());
        modal.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        modal.setKind(request.getKind());

        discoveryRepository.save(modal);

        attributeService.createAttributeContent(modal.getUuid(), request.getCustomAttributes(), Resource.DISCOVERY);

        return modal;
    }

    private void updateDiscovery(DiscoveryHistory modal, DiscoveryProviderDto response) {
        modal.setStatus(response.getStatus());
        modal.setEndTime(new Date());
        updateDiscoveryMeta(modal.getConnectorUuid(), response.getMeta(), modal);
        modal.setTotalCertificatesDiscovered(response.getTotalCertificatesDiscovered());
        discoveryRepository.save(modal);
    }

    private void updateDiscoveryMeta(UUID connectorUuid, List<MetadataAttribute> metaAttributes, DiscoveryHistory history) {
        metadataService.createMetadataDefinitions(connectorUuid, metaAttributes);
        metadataService.createMetadata(connectorUuid, history.getUuid(), null, null, metaAttributes, Resource.DISCOVERY, null);

    }

    private Boolean checkForCompletion(DiscoveryProviderDto response) {
        return response.getStatus() == DiscoveryStatus.IN_PROGRESS;
    }

    private List<Certificate> updateCertificates(Set<DiscoveryProviderCertificateDataDto> certificatesDiscovered,
                                                 DiscoveryHistory modal) {
        List<Certificate> allCerts = new ArrayList<>();
        if (certificatesDiscovered.isEmpty()) {
            logger.warn("No certificates were given by the provider for the discovery");
            return allCerts;
        }

        for (DiscoveryProviderCertificateDataDto certificate : certificatesDiscovered) {
            try {
                X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate.getBase64Content());
                boolean existingCertificate = certificateRepository.existsByFingerprint(CertificateUtil.getThumbprint(x509Cert.getEncoded()));
                Certificate entry = certificateService.createCertificateEntity(x509Cert);
                allCerts.add(entry);
                createDiscoveryCertificate(entry, modal, !existingCertificate);
                certificateService.updateCertificateEntity(entry);
                updateMeta(entry, certificate, modal);
                Map<String, Object> additionalInfo = new HashMap<>();
                additionalInfo.put("Discovery Name", modal.getName());
                additionalInfo.put("Discovery UUID", modal.getUuid());
                additionalInfo.put("Discovery Connector Name", modal.getConnectorName());
                additionalInfo.put("Discovery Kind", modal.getKind());
                certificateEventHistoryService.addEventHistory(
                        CertificateEvent.DISCOVERY,
                        CertificateEventStatus.SUCCESS,
                        "Discovered from Connector: " + modal.getConnectorName() + " via discovery: " + modal.getName(),
                        MetaDefinitions.serialize(additionalInfo),
                        entry
                );
            } catch (Exception e) {
                logger.error(e.getMessage());
                logger.error("Unable to create certificate for " + modal.toString());
            }
        }
        return allCerts;
    }

    private void updateCertificateIssuers(List<Certificate> certificates) {
        for (Certificate certificate : certificates) {
            try {
                certificateService.updateCertificateIssuer(certificate);
            } catch (NotFoundException e) {
                logger.warn("Unable to update the issuer for certificate {}", certificate.getSerialNumber());
            }
        }
    }

    private void createDiscoveryCertificate(Certificate entry, DiscoveryHistory modal, boolean newlyDiscovered) {
        DiscoveryCertificate discoveryCertificate = new DiscoveryCertificate();
        discoveryCertificate.setCommonName(entry.getCommonName());
        discoveryCertificate.setSerialNumber(entry.getSerialNumber());
        discoveryCertificate.setIssuerCommonName(entry.getIssuerCommonName());
        discoveryCertificate.setNotAfter(entry.getNotAfter());
        discoveryCertificate.setNotBefore(entry.getNotBefore());
        discoveryCertificate.setCertificateContent(entry.getCertificateContent());
        discoveryCertificate.setDiscovery(modal);
        discoveryCertificate.setNewlyDiscovered(newlyDiscovered);
        discoveryCertificateRepository.save(discoveryCertificate);
    }

    private void updateMeta(Certificate modal, DiscoveryProviderCertificateDataDto certificate, DiscoveryHistory history) {
        metadataService.createMetadataDefinitions(history.getConnectorUuid(), certificate.getMeta());
        metadataService.createMetadata(history.getConnectorUuid(), modal.getUuid(), history.getUuid(), history.getName(), certificate.getMeta(), Resource.CERTIFICATE, Resource.DISCOVERY);
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.GROUP, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getDiscoveryEntity(uuid);
    }
}

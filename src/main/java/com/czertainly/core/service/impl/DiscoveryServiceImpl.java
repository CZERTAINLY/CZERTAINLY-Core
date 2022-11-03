package com.czertainly.core.service.impl;

import com.czertainly.api.clients.DiscoveryApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.discovery.DiscoveryDto;

import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.connector.discovery.DiscoveryDataRequestDto;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderCertificateDataDto;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderDto;
import com.czertainly.api.model.connector.discovery.DiscoveryRequestDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.DiscoveryCertificate;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public List<DiscoveryHistoryDto> listDiscoveries(SecurityFilter filter) {
        return discoveryRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(DiscoveryHistory::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public DiscoveryHistoryDto getDiscovery(SecuredUUID uuid) throws NotFoundException {
        return getDiscoveryEntity(uuid).mapToDto();
    }

    public DiscoveryHistory getDiscoveryEntity(SecuredUUID uuid) throws NotFoundException {
        return discoveryRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Connector.class, uuid));
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
            discoveryRepository.delete(discovery);
            Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromUUID(discovery.getConnectorUuid()));
            discoveryApiClient.removeDiscovery(connector.mapToDto(), referenceUuid);
        } catch (ConnectorException e) {
            logger.warn("Failed to delete discovery in the connector. But core history is deleted");
            logger.warn(e.getMessage());
        }catch (Exception e) {
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
                logger.debug("Waiting {}ms.", SLEEP_TIME);
                Thread.sleep(SLEEP_TIME);

                response = discoveryApiClient.getDiscoveryData(connector.mapToDto(), getRequest, response.getUuid());

                if ((modal.getStartTime().getTime() - new Date().getTime()) / 1000 > MAXIMUM_WAIT_TIME
                        && !isReachedMaxTime && oldCertificateCount == response.getTotalCertificatesDiscovered()) {
                    isReachedMaxTime = true;
                    modal.setStatus(DiscoveryStatus.WARNING);
                    modal.setMessage(
                            "Discovery exceeded maximum time of " + MAXIMUM_WAIT_TIME/(60*60) + " hours. There are no changes in number of certificates discovered. Please abort the discovery if the provider is stuck in IN_PROGRESS");
                }
                discoveryRepository.save(modal);
                oldCertificateCount = response.getTotalCertificatesDiscovered();
                waitForCompletion = checkForCompletion(response);
            }

            Integer currentTotal = 0;
            List<DiscoveryProviderCertificateDataDto> certificatesDiscovered = new ArrayList<>();
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
            certificateService.updateIssuer();
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
        if(request.getConnectorUuid() == null){
            throw new ValidationException(ValidationError.create("Connector UUID is empty"));
        }
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));

        List<DataAttribute> attributes = connectorService.mergeAndValidateAttributes(
                SecuredUUID.fromString(request.getConnectorUuid()),
                FunctionGroupCode.DISCOVERY_PROVIDER,
                request.getAttributes(),
                request.getKind());

        DiscoveryHistory modal = new DiscoveryHistory();
        modal.setName(request.getName());
        modal.setConnectorName(connector.getName());
        modal.setStartTime(new Date());
        modal.setStatus(DiscoveryStatus.IN_PROGRESS);
        modal.setConnectorUuid(connector.getUuid().toString());
        modal.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        modal.setKind(request.getKind());

        discoveryRepository.save(modal);

        return modal;
    }

    private void updateDiscovery(DiscoveryHistory modal, DiscoveryProviderDto response) {
        modal.setStatus(response.getStatus());
        modal.setEndTime(new Date());
        modal.setMeta(MetaDefinitions.serialize(response.getMeta()));
        modal.setTotalCertificatesDiscovered(response.getTotalCertificatesDiscovered());
        discoveryRepository.save(modal);
    }

    private Boolean checkForCompletion(DiscoveryProviderDto response) {
        return response.getStatus() == DiscoveryStatus.IN_PROGRESS;
    }

    private List<Certificate> updateCertificates(List<DiscoveryProviderCertificateDataDto> certificatesDiscovered,
                                                 DiscoveryHistory modal) {
        List<Certificate> allCerts = new ArrayList<>();
        if (certificatesDiscovered.isEmpty()) {
            logger.warn("No certificates were given by the provider for the discovery");
            return allCerts;
        }

        for (DiscoveryProviderCertificateDataDto certificate : certificatesDiscovered) {
            try {
                X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate.getBase64Content());
                Certificate entry = certificateService.createCertificateEntity(x509Cert);
                updateMeta(entry, certificate, modal);
                allCerts.add(entry);
                createDiscoveryCertificate(entry, modal);
                Map<String, Object> additionalInfo = new HashMap<>();
                additionalInfo.put("Discovery Connector Name", modal.getConnectorName());
                additionalInfo.put("Discovery Kind", modal.getKind());
                additionalInfo.putAll(certificate.getMeta());
                certificateEventHistoryService.addEventHistory(CertificateEvent.DISCOVERY, CertificateEventStatus.SUCCESS, "Discovered from Connector: " + modal.getConnectorName(), MetaDefinitions.serialize(additionalInfo), entry);
            } catch (Exception e) {
                logger.error(e.getMessage());
                logger.error("Unable to create certificate for " + modal.toString());
            }
        }
        return allCerts;
    }

    private void createDiscoveryCertificate(Certificate entry, DiscoveryHistory modal) {
        DiscoveryCertificate discoveryCertificate = new DiscoveryCertificate();
        discoveryCertificate.setCommonName(entry.getCommonName());
        discoveryCertificate.setSerialNumber(entry.getSerialNumber());
        discoveryCertificate.setIssuerCommonName(entry.getIssuerCommonName());
        discoveryCertificate.setNotAfter(entry.getNotAfter());
        discoveryCertificate.setNotBefore(entry.getNotBefore());
        discoveryCertificate.setCertificateContent(entry.getCertificateContent());
        discoveryCertificate.setDiscovery(modal);
        discoveryCertificateRepository.save(discoveryCertificate);
    }

    private void updateMeta(Certificate modal, DiscoveryProviderCertificateDataDto certificate, DiscoveryHistory history) {
        Map<String, Object> meta = new HashMap<>();
        if (certificate.getMeta() != null) {
            meta = certificate.getMeta();
        }
        try {
            for (Map.Entry<String, Object> entry : MetaDefinitions.deserialize(modal.getMeta()).entrySet()) {
                if (entry.getKey().equals("discoverySource")) {
                    if (entry.getValue().equals(certificate.getMeta().getOrDefault("discoverySource", ""))) {
                        meta.put("discoverySource", entry.getValue());
                    } else {
                        meta.put("discoverySource", entry.getValue() + "," + certificate.getMeta().getOrDefault("discoverySource", ""));
                    }
                }
            }
        } catch (NullPointerException | IllegalStateException e) {
            logger.debug("Metadata is null for the certificate");
        }

        if (modal.getMeta() == null) {
            meta.put("discoverySource", certificate.getMeta().getOrDefault("discoverySource", ""));
        }
        modal.setMeta(MetaDefinitions.serialize(meta));

        certificateRepository.save(modal);

    }
}

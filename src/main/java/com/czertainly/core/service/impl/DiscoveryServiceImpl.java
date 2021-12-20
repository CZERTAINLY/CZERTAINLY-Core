package com.czertainly.core.service.impl;

import com.czertainly.api.DiscoveryApiClient;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.connector.FunctionGroupCode;
import com.czertainly.api.model.discovery.*;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.service.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceImpl.class);

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
    private CredentialService credentialService;
    @Autowired
    private DiscoveryCertificateRepository discoveryCertificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private CertValidationService certValidationService;

    private static final Integer MAXIMUM_CERTIFICATES_PER_PAGE = 100;
    private static final Integer SLEEP_TIME = 30 * 100;
    private static final Long MAXIMUM_WAIT_TIME = (long) (6 * 60 * 60); // Hours * Minutes * Seconds *

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.REQUEST)
    public List<DiscoveryHistoryDto> listDiscovery() {
        return discoveryRepository.findAll().stream().map(DiscoveryHistory::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.REQUEST)
    public DiscoveryHistoryDto getDiscovery(String uuid) throws NotFoundException {
        return getDiscoveryEntity(uuid).mapToDto();
    }

    public DiscoveryHistory getDiscoveryEntity(String uuid) throws NotFoundException {
        return discoveryRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Connector.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.DELETE)
    public void removeDiscovery(String uuid) throws NotFoundException {
        DiscoveryHistory discovery = discoveryRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Connector.class, uuid));
        for (DiscoveryCertificate cert : discoveryCertificateRepository.findByDiscovery(discovery)) {
        	try {
        		discoveryCertificateRepository.delete(cert);
        	}catch(Exception e) {
        		//todo
                logger.error(e.getMessage(), e);
        	}
            if (certificateRepository.findByCertificateContent(cert.getCertificateContent()) == null) {
                CertificateContent content = certificateContentRepository.findById(cert.getCertificateContent().getId())
                        .orElse(null);
                if (content != null) {
                	try {
                		certificateContentRepository.delete(content);
                	}catch(Exception e) {
                		logger.warn("Failed to delete the certificate.");
                		logger.warn(e.getMessage());
                	}
                }
            }
        }
        try {
            discoveryRepository.delete(discovery);
        }catch (Exception e){
        	logger.warn(e.getMessage());
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.DELETE)
    public void bulkRemoveDiscovery(List<String> discoveryUuids) throws NotFoundException {
        for(String uuid: discoveryUuids) {
            DiscoveryHistory discovery;
            try {
                discovery = discoveryRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(DiscoveryHistory.class, uuid));
            }catch (NotFoundException e){
                logger.warn("Unable to find the discovery with ID {}. It may have deleted", uuid);
                continue;
            }
            for (DiscoveryCertificate cert : discoveryCertificateRepository.findByDiscovery(discovery)) {
                try {
                    discoveryCertificateRepository.delete(cert);
                } catch (Exception e) {
                    logger.warn("Unable to delete the discovery certificate");
                    logger.warn(e.getMessage());
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
                discoveryRepository.delete(discovery);
            } catch (Exception e) {
                logger.warn(e.getMessage());
            }
        }
    }

    @Override
    @Async
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.DISCOVERY, operation = OperationType.CREATE)
    public void createDiscovery(DiscoveryDto request, DiscoveryHistory modal)
            throws NotFoundException, ConnectorException {

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(
                request.getConnectorUuid(),
                FunctionGroupCode.DISCOVERY_PROVIDER,
                request.getAttributes(),
                request.getDiscoveryType());

        try {
            DiscoveryProviderDto dtoRequest = new DiscoveryProviderDto();
            dtoRequest.setName(request.getName());
            dtoRequest.setConnectorUuid(request.getConnectorUuid());

            // Load complete credential data
            credentialService.loadFullCredentialData(attributes);
            dtoRequest.setAttributes(attributes);

            Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());
            DiscoveryProviderDto response = discoveryApiClient.discoverCertificate(connector.mapToDto(), dtoRequest);

            dtoRequest.setId(response.getId());
            Boolean waitForCompletion = checkForCompletion(response);
            boolean isReachedMaxTime = false;
            int oldCertificateCount = 0;
            while (waitForCompletion) {
                logger.debug("Waiting {}ms.", SLEEP_TIME);
                Thread.sleep(SLEEP_TIME);

                response = discoveryApiClient.discoverCertificate(connector.mapToDto(), dtoRequest);

                if ((modal.getStartTime().getTime() - new Date().getTime()) / 1000 > MAXIMUM_WAIT_TIME
                        && !isReachedMaxTime && oldCertificateCount == response.getTotalCertificatesDiscovered()) {
                    isReachedMaxTime = true;
                    modal.setStatus(DiscoveryStatus.WARNING);
                    modal.setMessage(
                            "Discovery exceeded maximum time of 6 hours. There are no changes in number of certificates discovered. Please abort the discovery if the provider is stuck in IN_PROGRESS");
                }
                discoveryRepository.save(modal);
                oldCertificateCount = response.getTotalCertificatesDiscovered();
                waitForCompletion = checkForCompletion(response);
            }

            Integer currentPage = 0;
            Integer totalPages = 0;
            List<DiscoveryProviderCertificateDataDto> certificatesDiscovered = new ArrayList<>();
            while (currentPage <= totalPages) {
                currentPage += 1;
                dtoRequest.setPageNumber(currentPage);
                response = discoveryApiClient.discoverCertificate(connector.mapToDto(), dtoRequest);
                totalPages = response.getTotalPages();
                if (response.getCertificateData().size() > MAXIMUM_CERTIFICATES_PER_PAGE) {
                    response.setStatus(DiscoveryStatus.FAILED);
                    updateDiscovery(modal, response);
                    logger.error("Too many content in response. Maximum processable is 100");
                    throw new InterruptedException(
                            "Too many content in response to process. Maximum processable is 100");
                }
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
        Connector connector = connectorService.getConnectorEntity(request.getConnectorUuid());

        List<AttributeDefinition> attributes = connectorService.mergeAndValidateAttributes(
                request.getConnectorUuid(),
                FunctionGroupCode.DISCOVERY_PROVIDER,
                request.getAttributes(),
                request.getDiscoveryType());

        DiscoveryHistory modal = new DiscoveryHistory();
        modal.setName(request.getName());
        modal.setConnectorName(connector.getName());
        modal.setStartTime(new Date());
        modal.setStatus(DiscoveryStatus.IN_PROGRESS);
        modal.setConnectorId(connector.getId());
        modal.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        modal.setDiscoveryType(request.getDiscoveryType());

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
        	}catch(Exception e) {
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
                    if (entry.getValue().equals(certificate.getDiscoverySource())) {
                        meta.put("discoverySource", entry.getValue());
                    } else {
                        meta.put("discoverySource", entry.getValue() + "," + certificate.getDiscoverySource());
                    }
                }
            }
        } catch (NullPointerException | IllegalStateException e) {
            logger.debug("Meta data is null for the certificate");
        }

        if (modal.getMeta() == null) {
            meta.put("discoverySource", certificate.getDiscoverySource());
        }
        modal.setMeta(MetaDefinitions.serialize(meta));

        certificateRepository.save(modal);

    }
}

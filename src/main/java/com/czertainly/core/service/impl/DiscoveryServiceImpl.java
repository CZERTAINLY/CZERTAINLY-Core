package com.czertainly.core.service.impl;

import com.czertainly.api.clients.DiscoveryApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.connector.discovery.DiscoveryDataRequestDto;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderCertificateDataDto;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderDto;
import com.czertainly.api.model.connector.discovery.DiscoveryRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.events.data.DiscoveryResult;
import com.czertainly.core.events.handlers.CertificateDiscoveredEventHandler;
import com.czertainly.core.events.handlers.DiscoveryFinishedEventHandler;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.messaging.producers.EventProducer;
import com.czertainly.core.messaging.producers.NotificationProducer;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.service.handler.CertificateHandler;
import com.czertainly.core.tasks.ScheduledJobInfo;
import com.czertainly.core.util.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;

@Service(Resource.Codes.DISCOVERY)
@Transactional
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceImpl.class);
    private static final Integer MAXIMUM_PARALLELISM = 5;
    private static final Integer MAXIMUM_CERTIFICATES_PER_PAGE = 100;
    private static final Integer SLEEP_TIME = 5 * 1000; // Seconds * Milliseconds - Retry of discovery for every 5 Seconds
    private static final Long MAXIMUM_WAIT_TIME = (long) (6 * 60 * 60); // Hours * Minutes * Seconds

    public static final Semaphore downloadCertSemaphore = new Semaphore(10);
    public static final Semaphore processCertSemaphore = new Semaphore(10);

    private EventProducer eventProducer;
    private NotificationProducer notificationProducer;
    private PlatformTransactionManager transactionManager;

    private AttributeEngine attributeEngine;
    private CertificateHandler certificateHandler;

    private TriggerService triggerService;
    private DiscoveryRepository discoveryRepository;
    private CertificateRepository certificateRepository;
    private DiscoveryApiClient discoveryApiClient;
    private ConnectorService connectorService;
    private CredentialService credentialService;
    private DiscoveryCertificateRepository discoveryCertificateRepository;
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setDiscoveryCertificateHandler(CertificateHandler certificateHandler) {
        this.certificateHandler = certificateHandler;
    }

    @Autowired
    public void setDiscoveryRepository(DiscoveryRepository discoveryRepository) {
        this.discoveryRepository = discoveryRepository;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setDiscoveryApiClient(DiscoveryApiClient discoveryApiClient) {
        this.discoveryApiClient = discoveryApiClient;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setCredentialService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Autowired
    public void setDiscoveryCertificateRepository(DiscoveryCertificateRepository discoveryCertificateRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
    }

    @Autowired
    public void setCertificateContentRepository(CertificateContentRepository certificateContentRepository) {
        this.certificateContentRepository = certificateContentRepository;
    }

    @Autowired
    public void setEventProducer(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Autowired
    public void setNotificationProducer(NotificationProducer notificationProducer) {
        this.notificationProducer = notificationProducer;
    }

    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public DiscoveryResponseDto listDiscoveries(final SecurityFilter filter, final SearchRequestDto request) {

        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<DiscoveryHistory>, CriteriaBuilder, CriteriaQuery, Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<DiscoveryHistoryDto> listedDiscoveriesDTOs = discoveryRepository.findUsingSecurityFilter(filter, List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get("created")))
                .stream()
                .map(DiscoveryHistory::mapToListDto).toList();
        final Long maxItems = discoveryRepository.countUsingSecurityFilter(filter, additionalWhereClause);

        final DiscoveryResponseDto responseDto = new DiscoveryResponseDto();
        responseDto.setDiscoveries(listedDiscoveriesDTOs);
        responseDto.setItemsPerPage(request.getItemsPerPage());
        responseDto.setPageNumber(request.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / request.getItemsPerPage()));
        return responseDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public DiscoveryHistoryDetailDto getDiscovery(SecuredUUID uuid) throws NotFoundException {
        DiscoveryHistory discoveryHistory = getDiscoveryEntity(uuid);
        DiscoveryHistoryDetailDto dto = discoveryHistory.mapToDto();
        dto.setMetadata(attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.DISCOVERY, discoveryHistory.getUuid())));
        dto.setAttributes(attributeEngine.getObjectDataAttributesContent(discoveryHistory.getConnectorUuid(), null, Resource.DISCOVERY, discoveryHistory.getUuid()));
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.DISCOVERY, uuid.getValue()));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public DiscoveryCertificateResponseDto getDiscoveryCertificates(SecuredUUID uuid,
                                                                    Boolean newlyDiscovered,
                                                                    int itemsPerPage,
                                                                    int pageNumber) throws NotFoundException {
        DiscoveryHistory discoveryHistory = getDiscoveryEntity(uuid);
        // Page number for the user always starts from 1. But for JPA, page number starts from 0
        Pageable p = PageRequest.of(pageNumber > 1 ? pageNumber - 1 : 0, itemsPerPage);
        List<DiscoveryCertificate> certificates;
        long maxItems;
        if (newlyDiscovered == null) {
            certificates = discoveryCertificateRepository.findByDiscovery(discoveryHistory, p);
            maxItems = discoveryCertificateRepository.countByDiscovery(discoveryHistory);
        } else {
            certificates = discoveryCertificateRepository.findByDiscoveryUuidAndNewlyDiscovered(discoveryHistory.getUuid(), newlyDiscovered, p);
            maxItems = discoveryCertificateRepository.countByDiscoveryAndNewlyDiscovered(discoveryHistory, newlyDiscovered);
        }

        final DiscoveryCertificateResponseDto responseDto = new DiscoveryCertificateResponseDto();
        responseDto.setCertificates(certificates.stream().map(DiscoveryCertificate::mapToDto).toList());
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
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DELETE)
    public void deleteDiscovery(SecuredUUID uuid) throws NotFoundException {
        DiscoveryHistory discovery = discoveryRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(DiscoveryHistory.class, uuid));
        long certsDeleted = discoveryCertificateRepository.deleteByDiscovery(discovery);
        logger.debug("Deleted {} discovery certificates", certsDeleted);

        int certContentsDeleted = certificateContentRepository.deleteUnusedCertificateContents();
        logger.debug("Deleted {} unused certificate contents", certContentsDeleted);

        attributeEngine.deleteAllObjectAttributeContent(Resource.DISCOVERY, discovery.getUuid());
        discoveryRepository.delete(discovery);
        triggerService.deleteTriggerAssociations(Resource.DISCOVERY, discovery.getUuid());

        try {
            String referenceUuid = discovery.getDiscoveryConnectorReference();
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
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DELETE)
    @Async
    public void bulkRemoveDiscovery(List<SecuredUUID> discoveryUuids) throws NotFoundException {
        UUID loggedUserUuid = UUID.fromString(AuthHelper.getUserIdentification().getUuid());
        for (SecuredUUID uuid : discoveryUuids) {
            deleteDiscovery(uuid);
        }
        notificationProducer.produceInternalNotificationMessage(Resource.DISCOVERY, null, NotificationRecipient.buildUserNotificationRecipient(loggedUserUuid), "Discovery histories have been deleted.", null);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public Long statisticsDiscoveryCount(SecurityFilter filter) {
        return discoveryRepository.countUsingSecurityFilter(filter, null);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.CREATE)
    public DiscoveryHistoryDetailDto createDiscovery(final DiscoveryDto request, final boolean saveEntity) throws AlreadyExistException, ConnectorException, AttributeException, NotFoundException {
        if (discoveryRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(DiscoveryHistory.class, request.getName());
        }
        if (request.getConnectorUuid() == null) {
            throw new ValidationException(ValidationError.create("Connector UUID is empty"));
        }
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(request.getConnectorUuid()));

        attributeEngine.validateCustomAttributesContent(Resource.DISCOVERY, request.getCustomAttributes());
        connectorService.mergeAndValidateAttributes(SecuredUUID.fromUUID(connector.getUuid()), FunctionGroupCode.DISCOVERY_PROVIDER, request.getAttributes(), request.getKind());

        DiscoveryHistory discovery = new DiscoveryHistory();
        discovery.setName(request.getName());
        discovery.setConnectorName(connector.getName());
        discovery.setStartTime(new Date());
        discovery.setStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorUuid(connector.getUuid());
        discovery.setKind(request.getKind());

        if (saveEntity) {
            discovery = discoveryRepository.save(discovery);
            attributeEngine.updateObjectCustomAttributesContent(Resource.DISCOVERY, discovery.getUuid(), request.getCustomAttributes());
            attributeEngine.updateObjectDataAttributesContent(connector.getUuid(), null, Resource.DISCOVERY, discovery.getUuid(), request.getAttributes());
            if (request.getTriggers() != null) {
                triggerService.createTriggerAssociations(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, discovery.getUuid(), request.getTriggers(), false);
                discovery = discoveryRepository.findWithTriggersByUuid(discovery.getUuid());
            }
            return discovery.mapToDto();
        }

        return null;
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.CREATE)
    public void runDiscoveryAsync(UUID discoveryUuid) {
        runDiscovery(discoveryUuid, null);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.CREATE)
    public DiscoveryHistoryDetailDto runDiscovery(UUID discoveryUuid, ScheduledJobInfo scheduledJobInfo) {
        UUID loggedUserUuid = UUID.fromString(AuthHelper.getUserIdentification().getUuid());

        // reload discovery modal with all association since it could be in separate transaction/session due to async
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        DiscoveryHistory discovery = discoveryRepository.findWithTriggersByUuid(discoveryUuid);
        logger.info("Starting discovery: name={}, uuid={}", discovery.getName(), discovery.getUuid());

        Connector connector;
        try {
            connector = connectorService.getConnectorEntity(SecuredUUID.fromString(discovery.getConnectorUuid().toString()));
        } catch (NotFoundException e) {
            updateDiscoveryState(discovery, DiscoveryStatus.FAILED, DiscoveryStatus.FAILED, "Discovery does not have associated provider", 0, 0, null);
            return finalizeDiscovery(discovery, loggedUserUuid, null, null);
        }

        // discover certificates by provider
        DiscoveryProviderDto providerResponse;
        try {
            providerResponse = discoverCertificatesByProvider(discovery, connector, status);
            if (status.isCompleted()) {
                status = transactionManager.getTransaction(new DefaultTransactionDefinition());
            }
        } catch (DiscoveryException e) {
            logger.error(e.getMessage());
            return finalizeDiscovery(discovery, loggedUserUuid, status, null);
        } catch (Exception e) {
            logger.error("Error in discovery '{}' at provider: {}", discovery.getName(), e.getMessage());
            updateDiscoveryState(discovery, DiscoveryStatus.FAILED, DiscoveryStatus.FAILED, "Error in provider: " + e.getMessage(), 0, 0, null);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return finalizeDiscovery(discovery, loggedUserUuid, status, null);
        }
        discoveryRepository.save(discovery);
        transactionManager.commit(status);

        // download and create discovered certificates
        List<DiscoveryProviderCertificateDataDto> duplicateCertificates = List.of();
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            duplicateCertificates = downloadDiscoveredCertificates(discovery, connector, providerResponse);
            updateDiscoveryState(discovery, DiscoveryStatus.IN_PROGRESS, null, "Discovered certificates downloaded from provider", null, null, null);
        } catch (DiscoveryException e) {
            logger.error(e.getMessage());
        }
        int downloadedCertificatesCount = (int) discoveryCertificateRepository.countByDiscovery(discovery);
        discovery.setTotalCertificatesDiscovered(downloadedCertificatesCount);

        // process duplicates
        for (DiscoveryProviderCertificateDataDto certificate : duplicateCertificates) {
            try {
                X509Certificate x509Cert = CertificateUtil.parseCertificate(certificate.getBase64Content());
                String fingerprint = CertificateUtil.getThumbprint(x509Cert.getEncoded());
                Certificate existingCertificate = certificateRepository.findByFingerprint(fingerprint).orElse(null);

                if (existingCertificate == null) {
                    logger.warn("Could not update metadata for duplicate discovery certificate. Certificate with fingerprint {} not found.", fingerprint);
                } else {
                    attributeEngine.updateMetadataAttributes(certificate.getMeta(), new ObjectAttributeContentInfo(discovery.getConnectorUuid(), Resource.CERTIFICATE, existingCertificate.getUuid(), Resource.DISCOVERY, discovery.getUuid(), discovery.getName()));
                }
            } catch (AttributeException e) {
                logger.error("Could not update metadata for duplicate discovery certificate {}.", certificate.getUuid());
            } catch (java.security.cert.CertificateException | NoSuchAlgorithmException e) {
                logger.error("Could not parse and process duplicate discovery certificate {}: {}", certificate.getUuid(), e.getMessage());
            }
        }
        String preProcessingMessage = discovery.getStatus() == DiscoveryStatus.IN_PROGRESS ? null : discovery.getMessage();
        long newlyDiscoveredCount = discoveryCertificateRepository.countByDiscoveryAndNewlyDiscovered(discovery, true);
        if (newlyDiscoveredCount == 0) {
            if (discovery.getStatus() == DiscoveryStatus.IN_PROGRESS) {
                discovery.setStatus(DiscoveryStatus.COMPLETED);
            }
            return finalizeDiscovery(discovery, loggedUserUuid, status, preProcessingMessage);
        } else {
            discovery.setStatus(DiscoveryStatus.PROCESSING);
        }

        discoveryRepository.save(discovery);
        transactionManager.commit(status);

        eventProducer.produceMessage(CertificateDiscoveredEventHandler.constructEventMessage(discovery.getUuid(), loggedUserUuid, scheduledJobInfo));
        return discovery.mapToDto();
    }

    private DiscoveryProviderDto discoverCertificatesByProvider(final DiscoveryHistory discovery, final Connector connector, TransactionStatus status) throws InterruptedException, DiscoveryException, ConnectorException, NotFoundException {
        DiscoveryRequestDto dtoRequest = new DiscoveryRequestDto();
        dtoRequest.setName(discovery.getName());
        dtoRequest.setKind(discovery.getKind());

        // Load complete credential data
        List<DataAttribute> dataAttributes = attributeEngine.getDefinitionObjectAttributeContent(
                AttributeType.DATA, connector.getUuid(), null, Resource.DISCOVERY, discovery.getUuid());
        credentialService.loadFullCredentialData(dataAttributes);
        dtoRequest.setAttributes(AttributeDefinitionUtils.getClientAttributes(dataAttributes));

        DiscoveryProviderDto response = discoveryApiClient.discoverCertificates(connector.mapToDto(), dtoRequest);

        logger.debug("Discovery response: name={}, uuid={}, status={}, total={}",
                discovery.getName(), discovery.getUuid(), response.getStatus(), response.getTotalCertificatesDiscovered());

        if (response.getUuid() == null) {
            updateDiscoveryState(discovery, DiscoveryStatus.FAILED, DiscoveryStatus.FAILED, "Discovery does not have associated discovery object at provider", 0, 0, null);
            throw new DiscoveryException(discovery.getName(), discovery.getMessage());
        }

        discovery.setDiscoveryConnectorReference(response.getUuid());

        DiscoveryDataRequestDto getRequest = new DiscoveryDataRequestDto();
        getRequest.setName(response.getName());
        getRequest.setKind(discovery.getKind());
        getRequest.setPageNumber(1);
        getRequest.setItemsPerPage(MAXIMUM_CERTIFICATES_PER_PAGE);

        boolean isReachedMaxTime = false;
        while (response.getStatus() == DiscoveryStatus.IN_PROGRESS) {
            logger.debug("Waiting {}ms for discovery to be completed: name={}, uuid={}", SLEEP_TIME, discovery.getName(), discovery.getUuid());
            Thread.sleep(SLEEP_TIME);

            try {
                response = discoveryApiClient.getDiscoveryData(connector.mapToDto(), getRequest, response.getUuid());
            } catch (ConnectorException e) {
                updateDiscoveryState(discovery, DiscoveryStatus.FAILED, response.getStatus(), "Discovery has failed on connector side while waiting for completion.", 0, response.getTotalCertificatesDiscovered(), null);
                throw new DiscoveryException(discovery.getName(), discovery.getMessage(), e);
            }

            logger.debug("Discovery response: name={}, uuid={}, status={}, total={}",
                    discovery.getName(), discovery.getUuid(), response.getStatus(), response.getTotalCertificatesDiscovered());

            if (!isReachedMaxTime && (new Date().getTime() - discovery.getStartTime().getTime()) / 1000 > MAXIMUM_WAIT_TIME) {
                isReachedMaxTime = true;

                String message = "Discovery %s exceeded maximum time of %d hours. Please abort the discovery if the provider is stuck in state %s."
                        .formatted(discovery.getName(), (int) (MAXIMUM_WAIT_TIME / (60 * 60)), DiscoveryStatus.IN_PROGRESS.getLabel());
                updateDiscoveryState(discovery, DiscoveryStatus.WARNING, response.getStatus(), message, null, response.getTotalCertificatesDiscovered(), null);
                discoveryRepository.save(discovery);
                transactionManager.commit(status);
            }
        }

        if (response.getTotalCertificatesDiscovered() == 0 && response.getStatus() == DiscoveryStatus.FAILED) {
            updateDiscoveryState(discovery, DiscoveryStatus.FAILED, response.getStatus(), "Discovery has failed on connector side without any certificates found.", 0, response.getTotalCertificatesDiscovered(), response.getMeta());
            throw new DiscoveryException(discovery.getName(), discovery.getMessage());
        }

        updateDiscoveryState(discovery, DiscoveryStatus.IN_PROGRESS, response.getStatus(), "Discovery completed at provider", null, response.getTotalCertificatesDiscovered(), response.getMeta());
        return response;
    }

    private List<DiscoveryProviderCertificateDataDto> downloadDiscoveredCertificates(final DiscoveryHistory discovery, final Connector connector, DiscoveryProviderDto response) throws DiscoveryException {
        int currentPage = 1;
        int currentTotal = 0;

        DiscoveryDataRequestDto getRequest = new DiscoveryDataRequestDto();
        getRequest.setName(response.getName());
        getRequest.setKind(discovery.getKind());
        getRequest.setPageNumber(1);
        getRequest.setItemsPerPage(MAXIMUM_CERTIFICATES_PER_PAGE);

        List<Future<?>> futures = new ArrayList<>();
        Set<String> uniqueCertificateContents = new HashSet<>();
        List<DiscoveryProviderCertificateDataDto> duplicateCertificates = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            while (currentTotal < response.getTotalCertificatesDiscovered()) {
                getRequest.setPageNumber(currentPage);
                getRequest.setItemsPerPage(MAXIMUM_CERTIFICATES_PER_PAGE);
                try {
                    response = discoveryApiClient.getDiscoveryData(connector.mapToDto(), getRequest, response.getUuid());
                } catch (ConnectorException e) {
                    handleDiscoveredCertificatesBatch(futures, discovery.getName());
                    updateDiscoveryState(discovery, DiscoveryStatus.FAILED, response.getStatus(), "Discovery has failed on connector side while downloading certificates.", null, null, null);
                    throw new DiscoveryException(discovery.getName(), discovery.getMessage(), e);
                }

                if (response.getCertificateData().isEmpty()) {
                    handleDiscoveredCertificatesBatch(futures, discovery.getName());
                    String message = String.format("Retrieved only %d certificates but provider discovered %d certificates in total.", currentTotal, response.getTotalCertificatesDiscovered());
                    updateDiscoveryState(discovery, DiscoveryStatus.WARNING, response.getStatus(), message, null, null, null);
                    throw new DiscoveryException(discovery.getName(), discovery.getMessage());
                }
                if (response.getCertificateData().size() > MAXIMUM_CERTIFICATES_PER_PAGE) {
                    handleDiscoveredCertificatesBatch(futures, discovery.getName());
                    updateDiscoveryState(discovery, DiscoveryStatus.FAILED, response.getStatus(), "Too many certificates (%d) in response at page %d. Maximum processable is %d.".formatted(response.getCertificateData().size(), currentPage, MAXIMUM_CERTIFICATES_PER_PAGE), null, null, null);
                    throw new DiscoveryException(discovery.getName(), discovery.getMessage());
                }

                futures.add(downloadDiscoveredCertificatesBatchAsync(discovery, response, connector, uniqueCertificateContents, duplicateCertificates, executor, currentPage));

                ++currentPage;
                currentTotal += response.getCertificateData().size();

                if (futures.size() >= MAXIMUM_PARALLELISM) {
                    handleDiscoveredCertificatesBatch(futures, discovery.getName());
                }
            }

            // Wait for all tasks to complete
            if (!futures.isEmpty()) {
                handleDiscoveredCertificatesBatch(futures, discovery.getName());
            }
        }
        return duplicateCertificates;
    }

    private Future<?> downloadDiscoveredCertificatesBatchAsync(final DiscoveryHistory discovery, final DiscoveryProviderDto response, final Connector connector, final Set<String> uniqueCertificateContents, final List<DiscoveryProviderCertificateDataDto> duplicateCertificates, final ExecutorService executor, final int currentPage) {
        // categorize certs and collect metadata definitions
        List<MetadataAttribute> metadataDefinitions = new ArrayList<>();
        Map<String, Set<BaseAttributeContent>> metadataContentsMapping = new HashMap<>();
        List<DiscoveryProviderCertificateDataDto> discoveredCertificates = new ArrayList<>();
        response.getCertificateData().forEach(c -> {
            if (uniqueCertificateContents.contains(c.getBase64Content())) {
                duplicateCertificates.add(c);
            } else {
                discoveredCertificates.add(c);
                uniqueCertificateContents.add(c.getBase64Content());
            }

            for (MetadataAttribute m : c.getMeta()) {
                Set<BaseAttributeContent> metadataContents = metadataContentsMapping.get(m.getUuid());
                if (metadataContents == null) {
                    metadataDefinitions.add(m);
                    metadataContents = new HashSet<>();
                    metadataContentsMapping.put(m.getUuid(), metadataContents);
                }

                metadataContents.addAll(m.getContent());
            }
        });

        // add/update certificate metadata to prevent creating duplicate definitions in parallel processing
        certificateHandler.updateMetadataDefinition(metadataDefinitions, metadataContentsMapping, connector.getUuid(), connector.getName());

        // run in separate virtual thread and continue
        return executor.submit(() -> {
            try {
                logger.trace("Waiting to download batch {} of discovered certificates for discovery {}.", currentPage, discovery.getName());
                downloadCertSemaphore.acquire();
                logger.trace("Downloading batch {} of discovered certificates for discovery {}.", currentPage, discovery.getName());

                certificateHandler.createDiscoveredCertificate(String.valueOf(currentPage), discovery, discoveredCertificates);
            } catch (InterruptedException e) {
                logger.error("Downloading batch {} of discovered certificates for discovery {} interrupted.", currentPage, discovery.getName(), e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Downloading batch {} of discovered certificates for discovery {} failed.", currentPage, discovery.getName(), e);
            } finally {
                logger.trace("Downloading batch {} of discovered certificates for discovery {} finalized. Released semaphore.", currentPage, discovery.getName());
                downloadCertSemaphore.release();
            }
        });
    }

    private void handleDiscoveredCertificatesBatch(List<Future<?>> futures, String discoveryName) {
        logger.debug("Waiting for {} download tasks for discovery {}", futures.size(), discoveryName);
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                logger.error("An error occurred during downloading discovered certificate of discovery {}: {}", discoveryName, e.getMessage(), e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        logger.debug("{} download tasks for discovery {} finished", futures.size(), discoveryName);
        futures.clear();
    }

    private void updateDiscoveryState(DiscoveryHistory discovery, DiscoveryStatus status, DiscoveryStatus connectorStatus, String message, Integer totalCertificatesDiscovered, Integer connectorTotalCertificatesDiscovered, List<MetadataAttribute> metadata) {
        discovery.setStatus(status);
        if (connectorStatus != null) discovery.setConnectorStatus(connectorStatus);
        if (message != null) discovery.setMessage(message);
        if (totalCertificatesDiscovered != null) discovery.setTotalCertificatesDiscovered(totalCertificatesDiscovered);
        if (connectorTotalCertificatesDiscovered != null) {
            discovery.setConnectorTotalCertificatesDiscovered(connectorTotalCertificatesDiscovered);
        }
        if (metadata != null && !metadata.isEmpty()) {
            try {
                attributeEngine.updateMetadataAttributes(metadata, new ObjectAttributeContentInfo(discovery.getConnectorUuid(), Resource.DISCOVERY, discovery.getUuid()));
            } catch (AttributeException e) {
                logger.warn("Failed to serialize discovery metadata");
            }
        }
    }

    private DiscoveryHistoryDetailDto finalizeDiscovery(DiscoveryHistory discovery, UUID loggedUserUuid, TransactionStatus status, String preProcessingMessage) {
        if (status == null || status.isCompleted()) {
            status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        }

        discovery.setEndTime(new Date());
        if (discovery.getStatus() == DiscoveryStatus.COMPLETED) {
            discovery.setMessage(preProcessingMessage == null ? "Discovery completed successfully" : "Discovery completed. " + preProcessingMessage);
        }

        discoveryRepository.save(discovery);
        transactionManager.commit(status);

        eventProducer.produceMessage(DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), loggedUserUuid, null, new DiscoveryResult(discovery.getStatus(), discovery.getMessage())));
        return discovery.mapToDto();
    }

    @Override
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        return discoveryRepository.findResourceObject(objectUuid, DiscoveryHistory_.name);
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        throw new NotSupportedException("Listing of resource objects is not supported for resource discoveries.");
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getDiscoveryEntity(uuid);
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {
        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(Resource.DISCOVERY, false);

        List<SearchFieldDataDto> fields = List.of(
                SearchHelper.prepareSearch(FilterField.CKI_NAME),
                SearchHelper.prepareSearch(FilterField.DISCOVERY_STATUS, Arrays.stream(DiscoveryStatus.values()).map(DiscoveryStatus::getCode).toList()),
                SearchHelper.prepareSearch(FilterField.DISCOVERY_START_TIME),
                SearchHelper.prepareSearch(FilterField.DISCOVERY_END_TIME),
                SearchHelper.prepareSearch(FilterField.DISCOVERY_TOTAL_CERT_DISCOVERED),
                SearchHelper.prepareSearch(FilterField.DISCOVERY_CONNECTOR_NAME, discoveryRepository.findDistinctConnectorName()),
                SearchHelper.prepareSearch(FilterField.DISCOVERY_KIND)

        );

        fields = new ArrayList<>(fields);
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));

        logger.debug("Searchable Fields by Groups: {}", searchFieldDataByGroupDtos);
        return searchFieldDataByGroupDtos;
    }
}

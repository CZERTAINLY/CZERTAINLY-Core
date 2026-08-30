package com.otilm.core.service.impl;

import com.otilm.api.clients.ApiClientConnectorInfo;
import com.otilm.api.exception.AlreadyExistException;
import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.NotSupportedException;
import com.otilm.api.exception.ValidationError;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.interfaces.client.v1.DiscoverySyncApiClient;
import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.client.certificate.DiscoveryResponseDto;
import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.connector.v2.ConnectorInterface;
import com.otilm.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.client.discovery.DiscoveryDto;
import com.otilm.api.model.client.discovery.DiscoveryListDto;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.common.attribute.common.BaseAttribute;
import com.otilm.api.model.connector.discovery.v2.DiscoverySupportedResourceDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.api.model.core.discovery.DiscoveryItemDto;
import com.otilm.api.model.core.discovery.DiscoveryMessageDto;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.api.model.core.scheduler.PaginationRequestDto;
import com.otilm.api.model.core.search.FilterFieldSource;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.otilm.core.client.ConnectorApiFactory;
import com.otilm.core.comparator.SearchFieldDataComparator;
import com.otilm.core.dao.entity.Connector;
import com.otilm.core.dao.entity.ConnectorInterfaceEntity;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.entity.DiscoveryMessage;
import com.otilm.core.dao.entity.Discovery_;
import com.otilm.core.dao.repository.CertificateContentRepository;
import com.otilm.core.dao.repository.ConnectorInterfaceRepository;
import com.otilm.core.dao.repository.ConnectorRepository;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryItemRepository;
import com.otilm.core.dao.repository.DiscoveryMessageRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.enums.FilterField;
import com.otilm.core.events.data.DiscoveryResult;
import com.otilm.core.events.handlers.DiscoveryFinishedEventHandler;
import com.otilm.core.exception.UnsupportedDiscoveryVersionException;
import com.otilm.core.mapper.discovery.DiscoveryDtoMapper;
import com.otilm.core.messaging.jms.producers.EventProducer;
import com.otilm.core.messaging.jms.producers.NotificationProducer;
import com.otilm.core.messaging.model.NotificationRecipient;
import com.otilm.core.model.auth.ResourceAction;
import com.otilm.core.security.authz.ExternalAuthorization;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.CommentInternalService;
import com.otilm.core.service.ConnectorInternalService;
import com.otilm.core.service.DiscoveryExternalService;
import com.otilm.core.service.DiscoveryInternalService;
import com.otilm.core.service.TriggerExternalService;
import com.otilm.core.service.TriggerInternalService;
import com.otilm.core.service.handler.discovery.DiscoveryProviderAdapter;
import com.otilm.core.service.handler.discovery.DiscoveryProviderAdapterFactory;
import com.otilm.core.service.writer.DiscoveryWriter;
import com.otilm.core.tasks.ScheduledJobInfo;
import com.otilm.core.util.AuthHelper;
import com.otilm.core.util.FilterPredicatesBuilder;
import com.otilm.core.util.RequestValidatorHelper;
import com.otilm.core.util.SearchHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service(Resource.Codes.DISCOVERY)
@Transactional
public class DiscoveryServiceImpl implements DiscoveryExternalService, DiscoveryInternalService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceImpl.class);

    /**
     * The largest page size the frontend offers ({@code DEFAULT_ITEMS_PER_PAGE_OPTIONS}). Clamping below it would
     * answer a user who picked 1000 with 100 rows and an itemsPerPage that disagrees with the control they used.
     * {@code WebAppConfig}'s own ceiling does not reach here — it binds Spring-resolved Pageables, and these arrive as
     * raw ints.
     */
    private static final int MAX_ITEMS_PER_PAGE = 1000;

    private static final String DISCOVERY_V2 = "v2";

    /** What the v2 contract defines item payloads for; anything else has no discovery shape to relay. */
    private static final Set<Resource> DISCOVERABLE_RESOURCES = EnumSet
            .of(Resource.CERTIFICATE, Resource.CRYPTOGRAPHIC_KEY);

    private static final String UNSUPPORTED_VERSION_MESSAGE = "The discovery's connector interface version is not supported.";

    private AttributeEngine attributeEngine;

    private TriggerInternalService triggerInternalService;
    private DiscoveryRepository discoveryRepository;
    private ConnectorApiFactory connectorApiFactory;
    private ConnectorInternalService connectorService;
    private DiscoveryCertificateRepository discoveryCertificateRepository;
    private CertificateContentRepository certificateContentRepository;

    private DiscoveryProviderAdapterFactory discoveryProviderAdapterFactory;
    private DiscoveryWriter discoveryWriter;
    private ConnectorRepository connectorRepository;
    private EventProducer eventProducer;
    private NotificationProducer notificationProducer;
    private TriggerExternalService triggerService;

    private CommentInternalService commentService;
    private DiscoveryMessageRepository discoveryMessageRepository;
    private ConnectorInterfaceRepository connectorInterfaceRepository;
    private DiscoveryItemRepository discoveryItemRepository;

    @Autowired
    public void setDiscoveryItemRepository(DiscoveryItemRepository discoveryItemRepository) {
        this.discoveryItemRepository = discoveryItemRepository;
    }

    @Autowired
    public void setConnectorInterfaceRepository(ConnectorInterfaceRepository connectorInterfaceRepository) {
        this.connectorInterfaceRepository = connectorInterfaceRepository;
    }

    @Autowired
    public void setCommentService(CommentInternalService commentService) {
        this.commentService = commentService;
    }

    @Autowired
    public void setDiscoveryMessageRepository(DiscoveryMessageRepository discoveryMessageRepository) {
        this.discoveryMessageRepository = discoveryMessageRepository;
    }

    @Autowired
    public void setConnectorRepository(ConnectorRepository connectorRepository) {
        this.connectorRepository = connectorRepository;
    }

    @Autowired
    public void setDiscoveryWriter(DiscoveryWriter discoveryWriter) {
        this.discoveryWriter = discoveryWriter;
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
    public void setTriggerService(TriggerExternalService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setDiscoveryProviderAdapterFactory(DiscoveryProviderAdapterFactory discoveryProviderAdapterFactory) {
        this.discoveryProviderAdapterFactory = discoveryProviderAdapterFactory;
    }

    @Autowired
    public void setTriggerInternalService(TriggerInternalService triggerInternalService) {
        this.triggerInternalService = triggerInternalService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setDiscoveryRepository(DiscoveryRepository discoveryRepository) {
        this.discoveryRepository = discoveryRepository;
    }

    @Autowired
    public void setConnectorApiFactory(ConnectorApiFactory connectorApiFactory) {
        this.connectorApiFactory = connectorApiFactory;
    }

    @Autowired
    public void setConnectorService(ConnectorInternalService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setDiscoveryCertificateRepository(DiscoveryCertificateRepository discoveryCertificateRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
    }

    @Autowired
    public void setCertificateContentRepository(CertificateContentRepository certificateContentRepository) {
        this.certificateContentRepository = certificateContentRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public DiscoveryResponseDto listDiscoveries(final SecurityFilter filter, final SearchRequestDto request) {

        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<Discovery>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause = (root,
                cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<DiscoveryListDto> listedDiscoveriesDTOs = discoveryRepository
                .findUsingSecurityFilter(filter, List.of(), additionalWhereClause, p,
                        (root, cb) -> cb.desc(root.get("created")))
                .stream()
                .map(DiscoveryDtoMapper::toListDto)
                .toList();
        final Long maxItems = discoveryRepository.countUsingSecurityFilter(filter, additionalWhereClause);

        final DiscoveryResponseDto responseDto = new DiscoveryResponseDto();
        responseDto.setDiscoveries(listedDiscoveriesDTOs);
        responseDto.setItemsPerPage(request.getItemsPerPage());
        responseDto.setPageNumber(request.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / request.getItemsPerPage()));
        return responseDto;
    }

    // The three relays below are keyed and gated on the CONNECTOR, not the run: DISCOVERY has no object access,
    // so gating there would silently skip the per-connector ACL. NOT_SUPPORTED because each one goes on to call
    // the connector, which must never happen inside a transaction.

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.ANY)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<DiscoverySupportedResourceDto> listDiscoveryResources(SecuredUUID connectorUuid)
            throws NotFoundException, ConnectorException {
        ApiClientConnectorInfo connector = connectorService.getConnectorForApiClient(connectorUuid.getValue());
        if (discoveryV2Interface(connectorUuid.getValue()).isEmpty()) {
            // A v1 connector discovers certificates and nothing else, so the answer is known without asking it.
            // Synthesized rather than empty: a client renders one shape for both generations.
            DiscoverySupportedResourceDto certificates = new DiscoverySupportedResourceDto();
            certificates.setResource(Resource.CERTIFICATE);
            return List.of(certificates);
        }
        return connectorApiFactory.getDiscoveryApiClientV2(connector).listSupportedResources(connector);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.ANY)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<BaseAttribute> getDiscoveryAttributes(SecuredUUID connectorUuid)
            throws NotFoundException, ConnectorException {
        ApiClientConnectorInfo connector = requireDiscoveryV2(connectorUuid);
        return connectorApiFactory.getDiscoveryApiClientV2(connector).listRunAttributes(connector);
    }

    @Override
    @ExternalAuthorization(resource = Resource.CONNECTOR, action = ResourceAction.ANY)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<BaseAttribute> getDiscoveryResourceAttributes(SecuredUUID connectorUuid, Resource resource)
            throws NotFoundException, ConnectorException {
        ApiClientConnectorInfo connector = requireDiscoveryV2(connectorUuid);
        // Checked before the connector is called at all: the client throws IllegalArgumentException for a
        // resource the contract defines no payload for, which would surface as a 500 rather than a 422.
        if (!DISCOVERABLE_RESOURCES.contains(resource)) {
            throw new ValidationException("Resource " + resource.getLabel() + " is not discoverable");
        }
        // Discoverable in general is not the same as discoverable by this connector, and the supported set is
        // never persisted -- it is relayed live -- so answering that question costs a call.
        if (!liveSupportedResources(connectorUuid.getValue()).contains(resource)) {
            throw new ValidationException(
                    "Connector " + connectorUuid.getValue() + " does not discover " + resource.getLabel());
        }
        return connectorApiFactory.getDiscoveryApiClientV2(connector).listResourceAttributes(connector, resource);
    }

    /** The connector, once its v2 discovery interface is known to exist — the only generation with a schema. */
    private ApiClientConnectorInfo requireDiscoveryV2(SecuredUUID connectorUuid) throws NotFoundException {
        ApiClientConnectorInfo connector = connectorService.getConnectorForApiClient(connectorUuid.getValue());
        if (discoveryV2Interface(connectorUuid.getValue()).isEmpty()) {
            throw new ValidationException(
                    "Connector " + connectorUuid.getValue() + " does not implement the v2 discovery interface");
        }
        return connector;
    }

    /**
     * Stores each resource's own attributes under that resource's wire code, which is the operation the request builder
     * later reads them back by. Without this a v2 run would send the connector an empty configuration for every
     * resource it targets, and the connector would scan on defaults.
     */
    private void storeResourceAttributes(DiscoveryDto request, Connector connector, Discovery discovery)
            throws AttributeException, NotFoundException {
        if (request.getResourceAttributes() == null) {
            return;
        }
        for (Map.Entry<Resource, List<RequestAttribute>> perResource : request.getResourceAttributes().entrySet()) {
            attributeEngine
                    .updateObjectDataAttributesContent(ObjectAttributeContentInfo
                            .builder(Resource.DISCOVERY, discovery.getUuid())
                            .connector(connector.getUuid())
                            .operation(perResource.getKey().getCode())
                            .build(), perResource.getValue());
        }
    }

    /**
     * Decides what a run may target, before anything is written.
     *
     * <p>
     * The two generations are mirror images: a v2 connector needs {@code resources}, since it discovers several kinds
     * and cannot guess which the caller meant, while a v1 connector must not be given any — it discovers certificates
     * and nothing else, so accepting the field would let a caller believe they had selected something. An empty list or
     * a null element never reaches here; bean validation refuses both.
     */
    private void validateRequestedResources(DiscoveryDto request, Connector connector,
            ConnectorInterfaceEntity discoveryV2) throws NotFoundException, ConnectorException {
        if (discoveryV2 == null) {
            if (request.getResources() != null) {
                throw new ValidationException("Connector " + connector.getUuid()
                        + " implements only the v1 discovery interface, which discovers certificates only");
            }
            return;
        }
        if (request.getResources() == null) {
            throw new ValidationException(
                    "resources is required for connector " + connector.getUuid() + ", which discovers several kinds");
        }
        // Asked inline, as the create path already asks the connector to validate attributes. Catching an
        // unsupported resource now costs one call; catching it at start costs a run that opens and immediately fails.
        List<Resource> supported = liveSupportedResources(connector.getUuid());
        List<Resource> unsupported = request
                .getResources()
                .stream()
                .filter(resource -> !supported.contains(resource))
                .toList();
        if (!unsupported.isEmpty()) {
            throw new ValidationException("Connector " + connector.getUuid() + " does not discover "
                    + unsupported.stream().map(Resource::getLabel).toList());
        }
    }

    /** What the connector says it can discover right now. Never persisted, so every caller asks. */
    private List<Resource> liveSupportedResources(UUID connectorUuid) throws NotFoundException, ConnectorException {
        ApiClientConnectorInfo connector = connectorService.getConnectorForApiClient(connectorUuid);
        return connectorApiFactory
                .getDiscoveryApiClientV2(connector)
                .listSupportedResources(connector)
                .stream()
                .map(DiscoverySupportedResourceDto::getResource)
                .toList();
    }

    private Optional<ConnectorInterfaceEntity> discoveryV2Interface(UUID connectorUuid) {
        return connectorInterfaceRepository
                .findByConnectorUuidAndInterfaceCodeAndVersion(connectorUuid, ConnectorInterface.DISCOVERY,
                        DISCOVERY_V2);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public DiscoveryDetailDto getDiscovery(SecuredUUID uuid) throws NotFoundException {
        Discovery discovery = getDiscoveryEntity(uuid);
        DiscoveryDetailDto dto = DiscoveryDtoMapper
                .toDetailDto(discovery, discoveryMessageRepository.countByDiscoveryUuid(discovery.getUuid()));
        dto
                .setMetadata(attributeEngine
                        .getMappedMetadataContent(
                                ObjectAttributeContentInfo.builder(Resource.DISCOVERY, discovery.getUuid()).build()));
        dto
                .setAttributes(attributeEngine
                        .getObjectDataAttributesContent(ObjectAttributeContentInfo
                                .builder(Resource.DISCOVERY, discovery.getUuid())
                                .connector(discovery.getConnectorUuid())
                                .build()));
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.DISCOVERY, uuid.getValue()));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public DiscoveryCertificateResponseDto getDiscoveryCertificates(SecuredUUID uuid, Boolean newlyDiscovered,
            int itemsPerPage, int pageNumber) throws NotFoundException {
        Discovery discovery = getDiscoveryEntity(uuid);
        // Page number for the user always starts from 1. But for JPA, page number starts from 0
        Pageable p = PageRequest.of(pageNumber > 1 ? pageNumber - 1 : 0, itemsPerPage);
        List<DiscoveryCertificate> certificates;
        Long maxItems;
        if (newlyDiscovered == null) {
            certificates = discoveryCertificateRepository.findByDiscovery(discovery, p);
            maxItems = discoveryCertificateRepository.countByDiscovery(discovery);
        } else {
            certificates = discoveryCertificateRepository
                    .findByDiscoveryUuidAndNewlyDiscovered(discovery.getUuid(), newlyDiscovered, p);
            maxItems = discoveryCertificateRepository.countByDiscoveryAndNewlyDiscovered(discovery, newlyDiscovered);
        }

        final DiscoveryCertificateResponseDto responseDto = new DiscoveryCertificateResponseDto();
        responseDto.setCertificates(certificates.stream().map(DiscoveryCertificate::mapToDto).toList());
        responseDto.setItemsPerPage(itemsPerPage);
        responseDto.setPageNumber(pageNumber);
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / itemsPerPage));
        return responseDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public PaginationResponseDto<DiscoveryMessageDto> getDiscoveryRunMessages(SecuredUUID uuid, int itemsPerPage,
            int pageNumber) throws NotFoundException {
        Discovery discovery = getDiscoveryEntity(uuid);
        // Page number for the user always starts from 1. But for JPA, page number starts from 0
        int pageSize = Math.clamp(itemsPerPage, 1, MAX_ITEMS_PER_PAGE);
        Pageable p = PageRequest.of(pageNumber > 1 ? pageNumber - 1 : 0, pageSize);

        Page<DiscoveryMessage> page = discoveryMessageRepository
                .findByDiscoveryUuidOrderByIdAsc(discovery.getUuid(), p);

        PaginationResponseDto<DiscoveryMessageDto> responseDto = new PaginationResponseDto<>();
        responseDto.setItems(page.getContent().stream().map(DiscoveryDtoMapper::toMessageDto).toList());
        responseDto.setItemsPerPage(pageSize);
        responseDto.setPageNumber(pageNumber);
        responseDto.setTotalItems(page.getTotalElements());
        responseDto.setTotalPages(page.getTotalPages());
        return responseDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public PaginationResponseDto<DiscoveryItemDto> getDiscoveryItems(SecuredUUID uuid, Resource resource,
            Boolean newlyDiscovered, int itemsPerPage, int pageNumber) throws NotFoundException {
        Discovery discovery = getDiscoveryEntity(uuid);
        int pageSize = Math.clamp(itemsPerPage, 1, MAX_ITEMS_PER_PAGE);
        long offset = (long) (pageNumber > 1 ? pageNumber - 1 : 0) * pageSize;
        // Both stores hold the enum member name, not the wire code the request carries.
        String resourceName = resource == null ? null : resource.name();

        List<DiscoveryItemDto> items = discoveryItemRepository
                .listItems(discovery.getUuid(), resourceName, newlyDiscovered, pageSize, offset)
                .stream()
                .map(DiscoveryDtoMapper::toItemDto)
                .toList();
        long totalItems = discoveryItemRepository.countItems(discovery.getUuid(), resourceName, newlyDiscovered);

        PaginationResponseDto<DiscoveryItemDto> responseDto = new PaginationResponseDto<>();
        responseDto.setItems(items);
        responseDto.setItemsPerPage(pageSize);
        responseDto.setPageNumber(pageNumber);
        responseDto.setTotalItems(totalItems);
        responseDto.setTotalPages((int) Math.ceil((double) totalItems / pageSize));
        return responseDto;
    }

    // The three lifecycle operations. NOT_SUPPORTED because each one calls the connector, which must never happen
    // inside a transaction; the adapter opens its own around each state change.

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.STOP)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void stopDiscovery(SecuredUUID uuid) throws NotFoundException {
        lifecycle(uuid, "stopped", DiscoveryProviderAdapter::stop);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.RESUME)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void resumeDiscovery(SecuredUUID uuid) throws NotFoundException {
        lifecycle(uuid, "resumed", DiscoveryProviderAdapter::resume);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.CANCEL)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cancelDiscovery(SecuredUUID uuid) throws NotFoundException {
        lifecycle(uuid, "cancelled", DiscoveryProviderAdapter::cancel);
    }

    /**
     * Routes one lifecycle operation to the adapter for the run's connector generation.
     *
     * <p>
     * A v1 adapter refuses with {@link UnsupportedOperationException}, which has no handler and would reach the client
     * as a 500. Translated here into the same 422 an illegal transition answers with: from a caller's side both mean
     * the same thing — this run cannot be asked to do this.
     */
    private void lifecycle(SecuredUUID uuid, String verb, BiConsumer<DiscoveryProviderAdapter, Discovery> operation)
            throws NotFoundException {
        Discovery discovery = getDiscoveryEntity(uuid);
        try {
            operation.accept(discoveryProviderAdapterFactory.forDiscovery(discovery), discovery);
        } catch (UnsupportedOperationException | UnsupportedDiscoveryVersionException e) {
            throw new ValidationException(
                    "Discovery " + uuid.getValue() + " cannot be " + verb + ": not supported by its connector version");
        }
    }

    // S8989 wants explicit rollbackFor on the class-level @Transactional for this checked exception; changing
    // rollback semantics is behavior, not cleanup, and the platform-wide convention is the default rollback.
    @SuppressWarnings("java:S8989")
    public Discovery getDiscoveryEntity(SecuredUUID uuid) throws NotFoundException {
        return discoveryRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(Discovery.class, uuid));
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DELETE)
    public void deleteDiscovery(SecuredUUID uuid) throws NotFoundException {
        Discovery discovery = discoveryRepository
                .findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Discovery.class, uuid));
        Long certsDeleted = discoveryCertificateRepository.deleteByDiscovery(discovery);
        logger.debug("Deleted {} discovery certificates", certsDeleted);

        Integer certContentsDeleted = certificateContentRepository.deleteUnusedCertificateContents();
        logger.debug("Deleted {} unused certificate contents", certContentsDeleted);

        attributeEngine.deleteObjectAttributeContent(Resource.DISCOVERY, discovery.getUuid());
        commentService.removeObjectComments(Resource.DISCOVERY, discovery.getUuid());
        discoveryRepository.delete(discovery);
        triggerInternalService.deleteTriggerAssociations(Resource.DISCOVERY, discovery.getUuid());

        try {
            String referenceUuid = discovery.getDiscoveryConnectorReference();
            if (referenceUuid != null && !referenceUuid.isEmpty()) {
                Connector connector = connectorRepository
                        .findByUuid(discovery.getConnectorUuid())
                        .orElseThrow(() -> new NotFoundException(Connector.class, discovery.getConnectorUuid()));
                ApiClientConnectorInfo connectorDto = connectorService.getConnectorForApiClient(connector.getUuid());
                DiscoverySyncApiClient client = connectorApiFactory.getDiscoveryApiClient(connectorDto);
                client.removeDiscovery(connectorDto, referenceUuid);
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
        notificationProducer
                .produceInternalNotificationMessage(Resource.DISCOVERY, null,
                        NotificationRecipient.buildUserNotificationRecipient(loggedUserUuid),
                        "Discovery histories have been deleted.", null);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public Long statisticsDiscoveryCount(SecurityFilter filter) {
        return discoveryRepository.countUsingSecurityFilter(filter, null);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.CREATE)
    public DiscoveryDetailDto createDiscovery(final DiscoveryDto request, final boolean saveEntity)
            throws AlreadyExistException, ConnectorException, AttributeException, NotFoundException {
        if (discoveryRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(Discovery.class, request.getName());
        }
        if (request.getConnectorUuid() == null) {
            throw new ValidationException(ValidationError.create("Connector UUID is empty"));
        }
        Connector connector = connectorRepository
                .findByUuid(UUID.fromString(request.getConnectorUuid()))
                .orElseThrow(() -> new NotFoundException(Connector.class, request.getConnectorUuid()));

        attributeEngine.validateCustomAttributesContent(Resource.DISCOVERY, request.getCustomAttributes());
        connectorService
                .mergeAndValidateAttributes(SecuredUUID.fromUUID(connector.getUuid()),
                        FunctionGroupCode.DISCOVERY_PROVIDER, request.getAttributes(), request.getKind());

        ConnectorInterfaceEntity discoveryV2 = discoveryV2Interface(connector.getUuid()).orElse(null);
        validateRequestedResources(request, connector, discoveryV2);

        Discovery discovery = new Discovery();
        discovery.setName(request.getName());
        discovery.setConnectorName(connector.getName());
        // The association is what routes every later operation to the v2 adapter; without it the run is a v1 run
        // no matter what the connector implements.
        if (discoveryV2 != null) {
            discovery.setConnectorInterfaceUuid(discoveryV2.getUuid());
            discovery.setResources(List.copyOf(request.getResources()));
        }
        // Captured here rather than at start: this is where a caller is still on the thread. A v2 run's import
        // runs from an agenda tick with no principal of its own, and authorization refuses CERTIFICATE:CREATE
        // without one, so the run has to remember who to act as.
        discovery.setStartedByUserUuid(AuthHelper.getActingUserUuidOrNull());
        discovery.setStartTime(OffsetDateTime.now(ZoneOffset.UTC));
        discovery.setStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorUuid(connector.getUuid());
        discovery.setKind(request.getKind());

        if (saveEntity) {
            discovery = discoveryRepository.save(discovery);
            attributeEngine
                    .updateObjectCustomAttributesContent(Resource.DISCOVERY, discovery.getUuid(),
                            request.getCustomAttributes());
            attributeEngine
                    .updateObjectDataAttributesContent(ObjectAttributeContentInfo
                            .builder(Resource.DISCOVERY, discovery.getUuid())
                            .connector(connector.getUuid())
                            .build(), request.getAttributes());
            storeResourceAttributes(request, connector, discovery);
            if (request.getTriggers() != null) {
                triggerService
                        .createTriggerAssociations(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY,
                                discovery.getUuid(), request.getTriggers(), false);
                discovery = discoveryRepository.findWithTriggersByUuid(discovery.getUuid());
            }
            // Zero without asking: the run was inserted a few lines above and nothing since then writes a message.
            return DiscoveryDtoMapper.toDetailDto(discovery, 0);
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
    public DiscoveryDetailDto runDiscovery(UUID discoveryUuid, ScheduledJobInfo scheduledJobInfo) {
        Discovery discovery = discoveryRepository.findByUuid(discoveryUuid).orElse(null);
        try {
            return discoveryProviderAdapterFactory.forDiscovery(discovery).start(discoveryUuid, scheduledJobInfo);
        } catch (UnsupportedDiscoveryVersionException e) {
            // A routing refusal must still end as a terminal, user-visible run state: the async caller swallows
            // whatever escapes here, and the scheduler expects a result rather than an exception.
            logger.warn("Discovery {} cannot be dispatched: {}", discoveryUuid, e.getMessage());
            // Resolved before the terminal write, so a failed lookup cannot leave the run FAILED without its event.
            UUID actingUserUuid = AuthHelper.getActingUserUuidOrNull();
            // The curated text, not e.getMessage(): the raw message carries the connector-reported version
            // string, which is unvalidated input — it stays in the log, like the REST handler's fixed body.
            // The writer maps the detail — see its javadoc for why this scope cannot re-read it.
            DiscoveryDetailDto failedDetail = discoveryWriter
                    .markDispatchRefused(discoveryUuid, UNSUPPORTED_VERSION_MESSAGE)
                    .orElseThrow(() -> e);
            eventProducer
                    .produceMessage(DiscoveryFinishedEventHandler
                            .constructEventMessage(discoveryUuid, actingUserUuid, null,
                                    new DiscoveryResult(DiscoveryStatus.FAILED, UNSUPPORTED_VERSION_MESSAGE)));
            return failedDetail;
        }
    }

    @Override
    public NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException {
        return discoveryRepository.findResourceObject(objectUuid, Discovery_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.DETAIL)
    public NameAndUuidDto getResourceObjectExternal(SecuredUUID objectUuid) throws NotFoundException {
        return discoveryRepository.findResourceObject(objectUuid.getValue(), Discovery_.name);
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters,
            PaginationRequestDto pagination) {
        throw new NotSupportedException("Listing of resource objects is not supported for resource discoveries.");
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getDiscoveryEntity(uuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.DISCOVERY, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {
        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine
                .getResourceSearchableFields(Resource.DISCOVERY, false);

        List<SearchFieldDataDto> fields = List
                .of(SearchHelper.prepareSearch(FilterField.CKI_NAME),
                        SearchHelper
                                .prepareSearch(FilterField.DISCOVERY_STATUS,
                                        Arrays.stream(DiscoveryStatus.values()).map(DiscoveryStatus::getCode).toList()),
                        SearchHelper.prepareSearch(FilterField.DISCOVERY_START_TIME),
                        SearchHelper.prepareSearch(FilterField.DISCOVERY_END_TIME),
                        SearchHelper.prepareSearch(FilterField.DISCOVERY_TOTAL_CERT_DISCOVERED),
                        SearchHelper
                                .prepareSearch(FilterField.DISCOVERY_CONNECTOR_NAME,
                                        discoveryRepository.findDistinctConnectorName()),
                        SearchHelper.prepareSearch(FilterField.DISCOVERY_KIND)

                );

        fields = new ArrayList<>(fields);
        fields.sort(new SearchFieldDataComparator());

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));

        logger.debug("Searchable Fields by Groups: {}", searchFieldDataByGroupDtos);
        return searchFieldDataByGroupDtos;
    }
}

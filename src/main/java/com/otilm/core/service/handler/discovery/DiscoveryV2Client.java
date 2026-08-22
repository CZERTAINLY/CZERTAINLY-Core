package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.DataAttribute;
import com.otilm.api.model.connector.discovery.v2.DiscoveryDrainRequestDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryResultsResponseDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryRunRequestDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryStatusResponseDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryV2ScopedRequestDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.ConnectorDto;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.client.ConnectorApiFactory;
import com.otilm.core.dao.entity.Connector;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.ConnectorRepository;
import com.otilm.core.service.CredentialInternalService;
import com.otilm.core.service.ResourceInternalService;
import com.otilm.core.util.AttributeDefinitionUtils;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The discovery v2 connector calls the tick workers make, with the request every one of them shares built in one place.
 *
 * <p>
 * <b>Why the request is rebuilt every time:</b> a discovery v2 connector keeps no Core-visible state, so identity, the
 * connector's own run handle and the run's whole attribute configuration are replayed on every call. Nothing here is
 * cached — the handle changes as the run progresses, and a stale one addresses a run the connector has moved on from.
 *
 * <p>
 * <b>{@code NOT_SUPPORTED}:</b> reading the run's attributes touches the database, but the connector call must never
 * run inside a transaction or hold a row lock — the platform-wide rule the {@code ConnectorApiClientArchTest} and
 * {@code TransactionalBoundaryArchTest} fences protect. Suspending any ambient transaction here makes that hold even
 * when a caller forgets.
 */
@Component
public class DiscoveryV2Client {

    private final ConnectorApiFactory connectorApiFactory;
    private final ConnectorRepository connectorRepository;
    private final AttributeEngine attributeEngine;
    private final CredentialInternalService credentialService;
    private final ResourceInternalService resourceService;

    public DiscoveryV2Client(ConnectorApiFactory connectorApiFactory, ConnectorRepository connectorRepository,
            AttributeEngine attributeEngine, CredentialInternalService credentialService,
            ResourceInternalService resourceService) {
        this.connectorApiFactory = connectorApiFactory;
        this.connectorRepository = connectorRepository;
        this.attributeEngine = attributeEngine;
        this.credentialService = credentialService;
        this.resourceService = resourceService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DiscoveryStatusResponseDto status(Discovery run)
            throws ConnectorException, NotFoundException, AttributeException {
        ConnectorDto connector = connectorOf(run);
        DiscoveryRunRequestDto request = new DiscoveryRunRequestDto();
        populate(request, run, connector);
        return connectorApiFactory.getDiscoveryApiClientV2(connector).status(connector, request);
    }

    /**
     * Pulls one page of results after the run's ingestion cursor.
     *
     * @param maxItems and {@code maxBytes} bound the page so a single response stays inside what the platform's
     * tunneled transport carries
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DiscoveryResultsResponseDto results(Discovery run, int maxItems, long maxBytes)
            throws ConnectorException, NotFoundException, AttributeException {
        ConnectorDto connector = connectorOf(run);
        DiscoveryDrainRequestDto request = new DiscoveryDrainRequestDto();
        populate(request, run, connector);
        request.setAfterSequence(run.getLastAppliedSequence());
        request.setMaxItems(maxItems);
        request.setMaxBytes(maxBytes);
        return connectorApiFactory.getDiscoveryApiClientV2(connector).results(connector, request);
    }

    /**
     * Fills the identity and configuration every v2 request carries. Run-level attributes are the run's own
     * {@code DISCOVERY} data attributes; per-resource ones are the same attributes stored under the resource's wire
     * code, which is how the create path separates them.
     */
    private void populate(DiscoveryV2ScopedRequestDto request, Discovery run, ConnectorDto connector)
            throws ConnectorException, NotFoundException, AttributeException {
        request.setRunId(run.getUuid());
        request.setMeta(run.getRunMeta());
        request.setAttributes(attributesFor(run, connector, null));
        request.setResourceAttributes(resourceAttributesFor(run, connector));
    }

    private Map<Resource, List<RequestAttribute>> resourceAttributesFor(Discovery run, ConnectorDto connector)
            throws ConnectorException, NotFoundException, AttributeException {
        if (run.getResources() == null || run.getResources().isEmpty()) {
            return Map.of();
        }
        Map<Resource, List<RequestAttribute>> byResource = new EnumMap<>(Resource.class);
        for (Resource resource : run.getResources()) {
            List<RequestAttribute> attributes = attributesFor(run, connector, resource.getCode());
            if (!attributes.isEmpty()) {
                byResource.put(resource, attributes);
            }
        }
        return byResource;
    }

    private List<RequestAttribute> attributesFor(Discovery run, ConnectorDto connector, String operation)
            throws ConnectorException, NotFoundException, AttributeException {
        List<DataAttribute> definitions = attributeEngine
                .getDefinitionObjectAttributeContent(AttributeType.DATA, UUID.fromString(connector.getUuid()),
                        operation, Resource.DISCOVERY, run.getUuid());
        // Credentials and referenced objects are stored as references; the connector needs the resolved
        // content, exactly as the v1 flow resolves it before its own call.
        credentialService.loadFullCredentialData(definitions);
        resourceService.loadResourceObjectContentData(definitions);
        return AttributeDefinitionUtils.getClientAttributes(definitions);
    }

    private ConnectorDto connectorOf(Discovery run) throws NotFoundException {
        Connector connector = connectorRepository
                .findByUuid(run.getConnectorUuid())
                .orElseThrow(() -> new NotFoundException(Connector.class, run.getConnectorUuid()));
        return connector.mapToDto();
    }
}

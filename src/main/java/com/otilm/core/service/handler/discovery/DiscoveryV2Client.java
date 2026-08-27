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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
 * connector's own run handle and the run's whole attribute configuration are replayed on every call. The handle is read
 * from the run each time — it changes as the run progresses, and a stale one addresses a run the connector has moved on
 * from.
 *
 * <p>
 * <b>{@code NOT_SUPPORTED}:</b> reading the run's attributes touches the database, but the connector call must never
 * run inside a transaction or hold a row lock — the platform-wide rule the {@code ConnectorApiClientArchTest} and
 * {@code TransactionalBoundaryArchTest} fences protect. Suspending any ambient transaction here makes that hold even
 * when a caller forgets.
 */
@Component
public class DiscoveryV2Client {

    /** Scope key the run's own attributes sit under; a resource's sit under its wire code. */
    static final String RUN_SCOPE = "";

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
        return drain(run, run.getLastAppliedSequence(), maxItems, maxBytes);
    }

    /**
     * The contract's full acknowledgement — a drain positioned past the last item the connector counted, which tells it
     * every item was received and its run state may be discarded. Asks for a single item because the response body is
     * not the point; the cursor it carries is.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void acknowledge(Discovery run, long highestSequence)
            throws ConnectorException, NotFoundException, AttributeException {
        drain(run, highestSequence, 1, 1024L);
    }

    private DiscoveryResultsResponseDto drain(Discovery run, long afterSequence, int maxItems, long maxBytes)
            throws ConnectorException, NotFoundException, AttributeException {
        ConnectorDto connector = connectorOf(run);
        DiscoveryDrainRequestDto request = new DiscoveryDrainRequestDto();
        populate(request, run, connector);
        request.setAfterSequence(afterSequence);
        request.setMaxItems(maxItems);
        // Clamped to the contract's cap: a configured value above it would produce a request the connector
        // rejects on every single drain.
        request.setMaxBytes(Math.min(maxBytes, DiscoveryDrainRequestDto.MAX_BYTES_CAP));
        return connectorApiFactory.getDiscoveryApiClientV2(connector).results(connector, request);
    }

    /**
     * Fills the identity and configuration every v2 request carries. Run-level attributes sit under {@link #RUN_SCOPE};
     * per-resource ones under the resource's wire code, which is how the create path separates them.
     */
    private void populate(DiscoveryV2ScopedRequestDto request, Discovery run, ConnectorDto connector)
            throws ConnectorException, NotFoundException, AttributeException {
        request.setRunId(run.getUuid());
        request.setMeta(run.getRunMeta());
        Map<String, List<DataAttribute>> scopes = resolvedScopes(run, connector);
        request.setAttributes(AttributeDefinitionUtils.getClientAttributes(scopes.getOrDefault(RUN_SCOPE, List.of())));
        Map<Resource, List<RequestAttribute>> byResource = new EnumMap<>(Resource.class);
        for (Resource resource : resourcesOf(run)) {
            List<RequestAttribute> attributes = AttributeDefinitionUtils
                    .getClientAttributes(scopes.getOrDefault(resource.getCode(), List.of()));
            if (!attributes.isEmpty()) {
                byResource.put(resource, attributes);
            }
        }
        request.setResourceAttributes(byResource);
    }

    /**
     * Every scope's definitions with their credential and referenced-object content loaded, exactly as the v1 flow
     * resolves it before its own call.
     */
    private Map<String, List<DataAttribute>> resolvedScopes(Discovery run, ConnectorDto connector)
            throws ConnectorException, NotFoundException, AttributeException {
        Map<String, List<DataAttribute>> scopes = definitionScopes(run, connector);
        // Resolved across all scopes at once, and only once per distinct definition: a credential the run and a
        // resource both declare is one lookup, not two, and for a SECRET reference a lookup is a connector round
        // trip. Duplicates take the resolved content from the instance that was actually resolved.
        List<DataAttribute> toResolve = new ArrayList<>();
        Map<String, DataAttribute> firstOfEach = new LinkedHashMap<>();
        Map<DataAttribute, DataAttribute> duplicates = new IdentityHashMap<>();
        for (List<DataAttribute> scope : scopes.values()) {
            for (DataAttribute definition : scope) {
                DataAttribute first = firstOfEach
                        .putIfAbsent(AttributeDefinitionUtils.serialize(definition), definition);
                if (first == null) {
                    toResolve.add(definition);
                } else {
                    duplicates.put(definition, first);
                }
            }
        }
        credentialService.loadFullCredentialData(toResolve);
        resourceService.loadResourceObjectContentData(toResolve);
        duplicates.forEach((duplicate, resolved) -> duplicate.setContent(resolved.getContent()));
        return scopes;
    }

    /** The run's own definitions under {@link #RUN_SCOPE}, then each targeted resource's under its wire code. */
    private Map<String, List<DataAttribute>> definitionScopes(Discovery run, ConnectorDto connector)
            throws NotFoundException, AttributeException {
        Map<String, List<DataAttribute>> scopes = new LinkedHashMap<>();
        scopes.put(RUN_SCOPE, fromEngine(run, connector, null));
        for (Resource resource : resourcesOf(run)) {
            scopes.put(resource.getCode(), fromEngine(run, connector, resource.getCode()));
        }
        return scopes;
    }

    private List<DataAttribute> fromEngine(Discovery run, ConnectorDto connector, String operation)
            throws NotFoundException, AttributeException {
        return attributeEngine
                .getDefinitionObjectAttributeContent(AttributeType.DATA, UUID.fromString(connector.getUuid()),
                        operation, Resource.DISCOVERY, run.getUuid());
    }

    private static List<Resource> resourcesOf(Discovery run) {
        return run.getResources() == null ? List.of() : run.getResources();
    }

    private ConnectorDto connectorOf(Discovery run) throws NotFoundException {
        Connector connector = connectorRepository
                .findByUuid(run.getConnectorUuid())
                .orElseThrow(() -> new NotFoundException(Connector.class, run.getConnectorUuid()));
        return connector.mapToDto();
    }
}

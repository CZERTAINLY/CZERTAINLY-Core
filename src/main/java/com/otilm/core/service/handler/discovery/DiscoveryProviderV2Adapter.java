package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.client.connector.v2.FeatureFlag;
import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.common.attribute.common.MetadataAttribute;
import com.otilm.api.model.connector.discovery.v2.DiscoveryInitiateResponseDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.ConnectorInterfaceEntity;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.ConnectorInterfaceRepository;
import com.otilm.core.dao.repository.DiscoveryMessageRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.exception.UnsupportedDiscoveryVersionException;
import com.otilm.core.mapper.discovery.DiscoveryDtoMapper;
import com.otilm.core.messaging.jms.configuration.DiscoveryWorkProperties;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.handler.ConnectorCapabilityService;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import com.otilm.core.tasks.ScheduledJobInfo;
import com.otilm.core.util.AttributeDefinitionUtils;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Opens and drives a v2 run at its connector.
 *
 * <p>
 * {@code start} is the handover, not the scan: it opens the run, records what the connector answered, and schedules the
 * first agenda rows. Everything after that belongs to the tick workers, so this returns as soon as the run is driveable
 * rather than waiting for results.
 *
 * <p>
 * Every failure here ends the run terminally. A v2 run that is left non-terminal has no owner — the caller is
 * asynchronous and the tick workers only drive runs that already have agenda rows — so the reaper would have to collect
 * it after its grace window rather than the failure being reported at once.
 */
@Component
public class DiscoveryProviderV2Adapter implements DiscoveryProviderAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryProviderV2Adapter.class);

    private static final String NOT_IMPLEMENTED = "The discovery v2 provider adapter is not implemented yet";

    /**
     * The contract's cap on a serialized run handle. Enforced on every meta-returning response, because the handle is
     * replayed on every later call: accepting an oversized one would produce a run whose every tick sends a request the
     * transport cannot carry.
     */
    static final int MAX_META_BYTES = 64 * 1024;

    private final DiscoveryRepository discoveryRepository;
    private final DiscoveryMessageRepository messageRepository;
    private final ConnectorInterfaceRepository connectorInterfaceRepository;
    private final DiscoveryV2Client client;
    private final DiscoveryWorkWriter workWriter;
    private final DiscoveryRunTerminator terminator;
    private final ConnectorCapabilityService capabilityService;
    private final TransactionHandler transactionHandler;
    private final DiscoveryWorkProperties workProperties;

    @SuppressWarnings("java:S107")
    public DiscoveryProviderV2Adapter(DiscoveryRepository discoveryRepository,
            DiscoveryMessageRepository messageRepository, ConnectorInterfaceRepository connectorInterfaceRepository,
            DiscoveryV2Client client, DiscoveryWorkWriter workWriter, DiscoveryRunTerminator terminator,
            ConnectorCapabilityService capabilityService, TransactionHandler transactionHandler,
            DiscoveryWorkProperties workProperties) {
        this.discoveryRepository = discoveryRepository;
        this.messageRepository = messageRepository;
        this.connectorInterfaceRepository = connectorInterfaceRepository;
        this.client = client;
        this.workWriter = workWriter;
        this.terminator = terminator;
        this.capabilityService = capabilityService;
        this.transactionHandler = transactionHandler;
        this.workProperties = workProperties;
    }

    @Override
    public DiscoveryDetailDto start(UUID discoveryUuid, ScheduledJobInfo scheduledJobInfo) {
        Discovery run = transactionHandler
                .runInNewTransaction(() -> discoveryRepository.findByUuid(discoveryUuid).orElse(null));
        if (run == null) {
            // Routed here for a run that no longer exists: the refusal signal, so the caller ends it the same way
            // it ends any run it cannot dispatch, rather than this throwing into an async caller.
            throw new UnsupportedDiscoveryVersionException("Discovery " + discoveryUuid + " no longer exists");
        }
        try {
            // Outside any transaction, by DiscoveryV2Client's own NOT_SUPPORTED boundary.
            validateResources(run);
            DiscoveryInitiateResponseDto response = client.initiate(run);
            recordInitiated(discoveryUuid, response);
            scheduleFirstTicks(discoveryUuid);
            return detailOf(discoveryUuid);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Discovery {} could not be started at its connector", discoveryUuid, e);
            terminator.end(discoveryUuid, DiscoveryStatus.FAILED, startFailureReason(e));
            return detailOf(discoveryUuid);
        }
    }

    /**
     * Refuses a run the connector cannot perform before opening it. The connector would reject the initiate anyway, but
     * a run that never opened is cheaper to explain than one that opened and failed, and the message names the resource
     * rather than relaying a connector error.
     */
    private void validateResources(Discovery run) throws Exception {
        List<Resource> requested = run.getResources() == null ? List.of() : run.getResources();
        if (requested.isEmpty()) {
            throw new IllegalStateException("the run targets no resources");
        }
        List<Resource> supported = client.supportedResources(run);
        List<Resource> unsupported = requested.stream().filter(resource -> !supported.contains(resource)).toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalStateException(
                    "the connector does not discover " + unsupported.stream().map(Resource::getCode).toList());
        }
    }

    /**
     * Records what the connector answered, under the run's lock.
     *
     * @throws IllegalStateException if the handle exceeds the cap — thrown before anything is persisted, so the run
     * fails rather than being driven with a handle it cannot replay
     */
    private void recordInitiated(UUID discoveryUuid, DiscoveryInitiateResponseDto response) {
        transactionHandler.runInNewTransaction(() -> {
            Discovery locked = discoveryRepository
                    .findWithLockByUuid(discoveryUuid)
                    .orElseThrow(() -> new IllegalStateException("Discovery " + discoveryUuid + " vanished mid-start"));
            enforceMetaCap(response.getMeta());
            locked.setRunMeta(response.getMeta());
            locked.setStoppable(stoppable(locked, response));
            locked.setStatus(DiscoveryStatus.IN_PROGRESS);
            locked.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
            locked.setStartTime(OffsetDateTime.now(ZoneOffset.UTC));
            return discoveryRepository.save(locked);
        });
    }

    private static void enforceMetaCap(List<MetadataAttribute> meta) {
        if (meta == null || meta.isEmpty()) {
            return;
        }
        String serialized = AttributeDefinitionUtils.serialize(meta);
        int size = serialized == null ? 0 : serialized.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_META_BYTES) {
            throw new IllegalStateException("meta size exceeded: " + size + " bytes, cap " + MAX_META_BYTES);
        }
    }

    /**
     * The connector may only narrow the interface-level {@code discoveryStopResume} flag, never widen it: claiming a
     * stoppable run without advertising the capability is a contract violation, and Core clamps rather than trusting
     * it. Undeclared falls back to the flag alone.
     */
    private boolean stoppable(Discovery run, DiscoveryInitiateResponseDto response) {
        ConnectorInterfaceEntity iface = run.getConnectorInterfaceUuid() == null
                ? null
                : connectorInterfaceRepository.findById(run.getConnectorInterfaceUuid()).orElse(null);
        boolean advertised = capabilityService.supports(iface, FeatureFlag.DISCOVERY_STOP_RESUME);
        return advertised && !Boolean.FALSE.equals(response.getStoppable());
    }

    /**
     * A run needs both agenda rows from the outset: STATUS reports progress and terminality, DRAIN pulls results. The
     * first delay of each ladder applies rather than now — a due-now row could be claimed by the sweep before this
     * transaction commits.
     */
    private void scheduleFirstTicks(UUID discoveryUuid) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (DiscoveryWorkType type : List.of(DiscoveryWorkType.STATUS, DiscoveryWorkType.DRAIN)) {
            workWriter.schedule(discoveryUuid, type, now.plus(workProperties.scheduleFor(type).delays().getFirst()));
        }
    }

    /** Carries the connector's own words where it gave any: a bare exception name tells an operator nothing. */
    private static String startFailureReason(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? "Discovery could not be started at its connector"
                : "Discovery could not be started at its connector: " + message;
    }

    private DiscoveryDetailDto detailOf(UUID discoveryUuid) {
        return transactionHandler
                .runInNewTransaction(() -> discoveryRepository
                        .findByUuid(discoveryUuid)
                        .map(run -> DiscoveryDtoMapper
                                .toDetailDto(run, messageRepository.countByDiscoveryUuid(discoveryUuid)))
                        .orElse(null));
    }

    @Override
    public void stop(Discovery discovery) {
        throw new IllegalStateException(NOT_IMPLEMENTED);
    }

    @Override
    public void resume(Discovery discovery) {
        throw new IllegalStateException(NOT_IMPLEMENTED);
    }

    @Override
    public void cancel(Discovery discovery) {
        throw new IllegalStateException(NOT_IMPLEMENTED);
    }
}

package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.ConnectorClientException;
import com.otilm.api.exception.ConnectorProblemException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.client.connector.v2.FeatureFlag;
import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.common.attribute.common.MetadataAttribute;
import com.otilm.api.model.connector.discovery.v2.DiscoveryInitiateResponseDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryStopResponseDto;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
 * Every {@code start} failure ends the run terminally. A v2 run that is left non-terminal has no owner — the caller is
 * asynchronous and the tick workers only drive runs that already have agenda rows — so the reaper would have to collect
 * it after its grace window rather than the failure being reported at once.
 *
 * <p>
 * <b>The lifecycle operations behave the opposite way</b> and throw. They answer a client that is waiting, so a refusal
 * is a response rather than a run outcome: an illegal transition raises {@link ValidationException}, which the platform
 * maps to 422. Only two things end a run here — a cancel, and a resume the connector cannot honour.
 */
@Component
public class DiscoveryProviderV2Adapter implements DiscoveryProviderAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryProviderV2Adapter.class);

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
            recordInitiated(discoveryUuid, response, scheduledJobInfo);
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
                    "the connector does not discover " + unsupported.stream().map(Resource::getLabel).toList());
        }
    }

    /**
     * Records what the connector answered, under the run's lock.
     *
     * @throws IllegalStateException if the handle exceeds the cap — thrown before anything is persisted, so the run
     * fails rather than being driven with a handle it cannot replay
     */
    private void recordInitiated(UUID discoveryUuid, DiscoveryInitiateResponseDto response,
            ScheduledJobInfo scheduledJobInfo) {
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
            // Stored because the run ends much later, in a tick worker that no longer has these: without them
            // the scheduled job never learns the outcome and hangs open.
            if (scheduledJobInfo != null) {
                locked.setScheduledJobHistoryUuid(scheduledJobInfo.jobHistoryUuid());
            }
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
        requireStatus(discovery, "stopped", DiscoveryStatus.IN_PROGRESS);
        // Both gates: the interface must advertise the capability, and this run must have been declared stoppable
        // at initiate. A run that was never checkpointable cannot be stopped even by a connector that can stop others.
        if (!advertisesStopResume(discovery) || !Boolean.TRUE.equals(discovery.getStoppable())) {
            throw new ValidationException("Discovery " + discovery.getUuid() + " cannot be stopped");
        }
        UUID discoveryUuid = discovery.getUuid();
        DiscoveryStopResponseDto response = call(discoveryUuid, "stop", () -> client.stop(discovery));
        transactionHandler.runInNewTransaction(() -> {
            Discovery locked = lock(discoveryUuid);
            enforceMetaCap(response.getMeta());
            locked.setRunMeta(response.getMeta());
            locked.setStatus(DiscoveryStatus.STOPPED);
            locked.setStoppedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return discoveryRepository.save(locked);
        });
        // The agenda stays, parked: a stopped run is resumable, and rebuilding its rows on resume would lose the
        // attempt counters that bound a run the connector has gone quiet on.
        workWriter.resetAttempt(discoveryUuid, DiscoveryWorkType.STATUS, 0);
        workWriter.resetAttempt(discoveryUuid, DiscoveryWorkType.DRAIN, 0);
    }

    @Override
    public void resume(Discovery discovery) {
        requireStatus(discovery, "resumed", DiscoveryStatus.STOPPED);
        // The flag alone, deliberately: the run's own snapshot describes whether it could be stopped, and the
        // connector's 410 is the authority on whether its checkpoint still exists.
        if (!advertisesStopResume(discovery)) {
            throw new ValidationException("Discovery " + discovery.getUuid() + " cannot be resumed");
        }
        UUID discoveryUuid = discovery.getUuid();
        DiscoveryInitiateResponseDto response;
        try {
            response = client.resume(discovery);
        } catch (Exception e) {
            if (isCheckpointLost(e)) {
                disposeCheckpointLost(discoveryUuid);
                return;
            }
            throw failed(discoveryUuid, "resume", e);
        }
        transactionHandler.runInNewTransaction(() -> {
            Discovery locked = lock(discoveryUuid);
            enforceMetaCap(response.getMeta());
            locked.setRunMeta(response.getMeta());
            locked.setStoppable(stoppable(locked, response));
            locked.setStatus(DiscoveryStatus.IN_PROGRESS);
            locked.setStoppedAt(null);
            return discoveryRepository.save(locked);
        });
        workWriter.expedite(discoveryUuid, DiscoveryWorkType.STATUS, OffsetDateTime.now(ZoneOffset.UTC));
        workWriter.expedite(discoveryUuid, DiscoveryWorkType.DRAIN, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public void cancel(Discovery discovery) {
        requireStatus(discovery, "cancelled", DiscoveryStatus.IN_PROGRESS, DiscoveryStatus.STOPPED);
        UUID discoveryUuid = discovery.getUuid();
        // 404 is not a failure: it says the connector no longer tracks the run, which is the state cancel asked
        // for. The client hands the status back rather than throwing precisely so this can be read, not caught.
        call(discoveryUuid, "cancel", () -> client.cancel(discovery));
        terminator.end(discoveryUuid, DiscoveryStatus.CANCELLED, "Discovery cancelled");
    }

    /**
     * A resume the connector cannot honour: its checkpoint is gone, so the run can never be driven again.
     *
     * <p>
     * The staged items stay. They were really discovered, and a client can still read them — they are simply never
     * processed, because the run that would have processed them no longer exists.
     */
    private void disposeCheckpointLost(UUID discoveryUuid) {
        logger.warn("Discovery {} cannot be resumed: the connector no longer holds its checkpoint", discoveryUuid);
        terminator.end(discoveryUuid, DiscoveryStatus.FAILED, "checkpoint lost");
    }

    /**
     * Keyed on HTTP status, not on {@code ErrorCode}: over MQ the proxy classifies by status alone and discards the
     * problem body, so the code is absent on one of the two transports the platform supports.
     */
    private static boolean isCheckpointLost(Exception e) {
        HttpStatus status = statusOf(e);
        return status == HttpStatus.NOT_FOUND || status == HttpStatus.GONE;
    }

    private static HttpStatus statusOf(Exception e) {
        if (e instanceof ConnectorProblemException problem) {
            return problem.getHttpStatus();
        }
        if (e instanceof ConnectorClientException client) {
            return client.getHttpStatus();
        }
        return null;
    }

    private void requireStatus(Discovery discovery, String verb, DiscoveryStatus... legal) {
        // Compared by identity rather than List.contains: the status column is nullable, and an immutable list
        // throws on a null argument instead of answering false.
        if (Arrays.stream(legal).noneMatch(status -> status == discovery.getStatus())) {
            // 422 rather than 409: the platform's state-transition convention, and Core maps no CONFLICT for run state.
            throw new ValidationException(
                    "Discovery " + discovery.getUuid() + " is " + discovery.getStatus() + " and cannot be " + verb);
        }
    }

    private boolean advertisesStopResume(Discovery discovery) {
        ConnectorInterfaceEntity iface = discovery.getConnectorInterfaceUuid() == null
                ? null
                : connectorInterfaceRepository.findById(discovery.getConnectorInterfaceUuid()).orElse(null);
        return capabilityService.supports(iface, FeatureFlag.DISCOVERY_STOP_RESUME);
    }

    private Discovery lock(UUID discoveryUuid) {
        return discoveryRepository
                .findWithLockByUuid(discoveryUuid)
                .orElseThrow(() -> new IllegalStateException("Discovery " + discoveryUuid + " vanished mid-operation"));
    }

    /** Runs a connector call, letting a validation refusal through and wrapping everything else. */
    private <T> T call(UUID discoveryUuid, String operation, ConnectorCall<T> connectorCall) {
        try {
            return connectorCall.execute();
        } catch (ValidationException e) {
            // The connector's own refusal -- a run past the point of no return -- already carries the right status.
            throw e;
        } catch (Exception e) {
            throw failed(discoveryUuid, operation, e);
        }
    }

    private IllegalStateException failed(UUID discoveryUuid, String operation, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        logger.error("Discovery {} {} failed at its connector", discoveryUuid, operation, e);
        return new IllegalStateException("Discovery " + operation + " failed at its connector", e);
    }

    @FunctionalInterface
    private interface ConnectorCall<T> {
        T execute() throws Exception;
    }
}

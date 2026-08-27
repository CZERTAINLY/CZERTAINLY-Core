package com.otilm.core.service.writer;

import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writer bean for discovery bookkeeping and progress reporting.
 *
 * <p>
 * These writes must survive the rollback of the import unit whose outcome they record — otherwise the reason a
 * certificate was lost is destroyed by the failure that lost it. That isolation is the caller's responsibility, not
 * this bean's: methods here use {@code REQUIRED} so they compose, and the orchestrator wraps each call in
 * {@code TransactionHandler.runInNewTransaction(...)} after the import unit has returned. Calling them from inside a
 * doomed transaction discards the write.
 */
@Service
public class DiscoveryWriter {

    private final DiscoveryCertificateRepository discoveryCertificateRepository;
    private final DiscoveryRepository discoveryRepository;

    public DiscoveryWriter(DiscoveryCertificateRepository discoveryCertificateRepository,
            DiscoveryRepository discoveryRepository) {
        this.discoveryCertificateRepository = discoveryCertificateRepository;
        this.discoveryRepository = discoveryRepository;
    }

    /**
     * Marks a discovered-certificate row as handled, whether or not the outcome was a clean import.
     *
     * @param processedError the shaped reason shared by every listed row, or {@code null} when they imported cleanly or
     * were ignored
     */
    @Transactional
    public void markProcessed(Collection<UUID> discoveryCertificateUuids, String processedError) {
        discoveryCertificateRepository.markProcessed(discoveryCertificateUuids, processedError);
    }

    /**
     * Records a reason against a row that finished earlier in the run, leaving {@code processed} untouched.
     */
    @Transactional
    public void recordProcessedError(Collection<UUID> discoveryCertificateUuids, String processedError) {
        discoveryCertificateRepository.updateProcessedError(discoveryCertificateUuids, processedError);
    }

    /**
     * Reports progress by discovery identifier rather than through the shared {@code Discovery} instance — see
     * {@code DiscoverySource} for why that entity must not reach a worker.
     */
    @Transactional
    public void updateProgressMessage(UUID discoveryUuid, String message) {
        discoveryRepository.updateMessage(discoveryUuid, message);
    }

    /**
     * Ends a run that was refused before dispatch — terminal FAILED on both statuses, the given user-visible message,
     * the end timestamp — and returns the terminal detail. Empty for an unknown uuid: the refusal path only fires for a
     * loaded run.
     *
     * <p>
     * The detail is mapped in here, inside this write's transaction, because the refusing caller cannot safely re-read
     * it: a {@code NOT_SUPPORTED} caller runs all its reads in one transaction-less synchronization scope sharing a
     * single {@code EntityManager}, so a re-read there resolves to the pre-refusal entity in its first-level cache.
     * </p>
     */
    @Transactional
    public Optional<DiscoveryDetailDto> markDispatchRefused(UUID discoveryUuid, String message) {
        return discoveryRepository.findByUuid(discoveryUuid).map(discovery -> {
            discovery.setStatus(DiscoveryStatus.FAILED);
            discovery.setConnectorStatus(DiscoveryStatus.FAILED);
            discovery.setMessage(message);
            discovery.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
            return discovery.mapToDto();
        });
    }
}

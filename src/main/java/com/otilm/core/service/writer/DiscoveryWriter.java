package com.otilm.core.service.writer;

import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writer bean for discovery bookkeeping and progress reporting.
 *
 * <p>These writes must survive the rollback of the import unit whose outcome they record — otherwise the reason
 * a certificate was lost is destroyed by the failure that lost it. That isolation is the caller's
 * responsibility, not this bean's: methods here use {@code REQUIRED} so they compose, and the orchestrator
 * wraps each call in {@code TransactionHandler.runInNewTransaction(...)} after the import unit has returned.
 * Calling them from inside a doomed transaction discards the write.
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
     * @param processedError the shaped reason, or {@code null} when the row imported cleanly or was ignored
     */
    @Transactional
    public void markProcessed(UUID discoveryCertificateUuid, String processedError) {
        discoveryCertificateRepository.markProcessed(discoveryCertificateUuid, processedError);
    }

    /**
     * Records a reason against a row that finished earlier in the run, leaving {@code processed} untouched.
     */
    @Transactional
    public void recordProcessedError(UUID discoveryCertificateUuid, String processedError) {
        discoveryCertificateRepository.updateProcessedError(discoveryCertificateUuid, processedError);
    }

    /**
     * Reports progress by discovery identifier rather than through the shared {@code DiscoveryHistory}
     * instance, so concurrent workers no longer mutate and save one detached entity between them.
     */
    @Transactional
    public void updateProgressMessage(UUID discoveryUuid, String message) {
        discoveryRepository.updateMessage(discoveryUuid, message);
    }
}

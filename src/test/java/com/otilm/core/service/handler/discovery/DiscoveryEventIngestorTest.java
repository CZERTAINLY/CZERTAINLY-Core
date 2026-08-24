package com.otilm.core.service.handler.discovery;

import com.otilm.api.model.common.enums.cryptography.KeyAlgorithm;
import com.otilm.api.model.common.enums.cryptography.KeyType;
import com.otilm.api.model.connector.discovery.v2.DiscoveredItemDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveredKeyDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryResultsResponseDto;
import com.otilm.api.model.connector.discovery.v2.event.DiscoveryResultBatchEvent;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.CryptographicKeyItem;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.repository.CryptographicKeyItemRepository;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.service.handler.CertificateHandler;
import com.otilm.core.service.writer.discovery.DiscoveryItemWriter;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The ingestor decisions a database test cannot pin down: what happens to work addressed to a run that no longer
 * exists, and how a key's inventory correlation is resolved before it is staged.
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryEventIngestorTest {

    @Mock
    private DiscoveryRepository discoveryRepository;
    @Mock
    private DiscoveryItemWriter itemWriter;
    @Mock
    private DiscoveryWorkWriter workWriter;
    @Mock
    private CertificateHandler certificateHandler;
    @Mock
    private CryptographicKeyItemRepository keyItemRepository;
    @Mock
    private DiscoveryCertificateRepository certificateRepository;

    private DiscoveryEventIngestor ingestor;

    @BeforeEach
    void setUp() {
        ingestor = new DiscoveryEventIngestor(discoveryRepository, itemWriter, workWriter, certificateHandler,
                keyItemRepository, certificateRepository);
    }

    @Test
    void pageForADeletedRun_isDroppedRatherThanFailing() {
        UUID gone = UUID.randomUUID();
        when(discoveryRepository.findWithLockByUuid(gone)).thenReturn(Optional.empty());

        // The agenda row cascaded away with the run, so this is a redelivered obsolete tick, not a fault:
        // throwing would send it round the broker's redelivery loop forever.
        ingestor.applyDrainPage(gone, page(keyItem(1, "key-a", "fp-a")));

        verifyNoInteractions(itemWriter, certificateHandler);
    }

    @Test
    void advisoryEventForADeletedRun_isDroppedRatherThanFailing() {
        UUID gone = UUID.randomUUID();
        when(discoveryRepository.findWithLockByUuid(gone)).thenReturn(Optional.empty());

        ingestor.applyAdvisoryEvent(gone, new DiscoveryResultBatchEvent());

        verifyNoInteractions(workWriter);
    }

    @Test
    void keyAlreadyInInventory_isStagedAsNotNewlyDiscovered() {
        Discovery run = run();
        when(keyItemRepository.findByFingerprint("fp-known")).thenReturn(Optional.of(new CryptographicKeyItem()));

        ingestor.applyDrainPage(run.getUuid(), page(keyItem(1, "key-a", "fp-known")));

        verify(itemWriter).stage(eq(run.getUuid()), any(DiscoveredItemDto.class), eq(false));
    }

    @Test
    void keyMissingFromInventory_isStagedAsNewlyDiscovered() {
        Discovery run = run();
        when(keyItemRepository.findByFingerprint("fp-new")).thenReturn(Optional.empty());

        ingestor.applyDrainPage(run.getUuid(), page(keyItem(1, "key-a", "fp-new")));

        verify(itemWriter).stage(eq(run.getUuid()), any(DiscoveredItemDto.class), eq(true));
    }

    @Test
    void keyWithoutAFingerprint_isStagedAsNewlyDiscoveredWithoutAnInventoryLookup() {
        Discovery run = run();

        ingestor.applyDrainPage(run.getUuid(), page(keyItem(1, "key-a", null)));

        verify(keyItemRepository, never()).findByFingerprint(any());
        verify(itemWriter).stage(eq(run.getUuid()), any(DiscoveredItemDto.class), eq(true));
    }

    private Discovery run() {
        Discovery run = new Discovery();
        run.setUuid(UUID.randomUUID());
        run.setStatus(DiscoveryStatus.IN_PROGRESS);
        when(discoveryRepository.findWithLockByUuid(run.getUuid())).thenReturn(Optional.of(run));
        return run;
    }

    private DiscoveryResultsResponseDto page(DiscoveredItemDto... items) {
        DiscoveryResultsResponseDto page = new DiscoveryResultsResponseDto();
        page.setItems(List.of(items));
        page.setHighestSequence(1L);
        page.setMore(false);
        return page;
    }

    private DiscoveredItemDto keyItem(long sequence, String uniqueRef, String fingerprint) {
        DiscoveredKeyDto payload = new DiscoveredKeyDto();
        payload.setType(KeyType.PUBLIC_KEY);
        payload.setAlgorithm(KeyAlgorithm.RSA);
        payload.setFingerprint(fingerprint);
        DiscoveredItemDto item = new DiscoveredItemDto();
        item.setSequence(sequence);
        item.setUniqueRef(uniqueRef);
        item.setPayload(payload);
        return item;
    }
}

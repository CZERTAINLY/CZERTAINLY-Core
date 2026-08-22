package com.otilm.core.integration.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.CertificateContent;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.entity.DiscoveryWork;
import com.otilm.core.dao.repository.CertificateContentRepository;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.dao.repository.DiscoveryWorkRepository;
import com.otilm.core.events.handlers.CertificateDiscoveredEventHandler;
import com.otilm.core.messaging.jms.producers.DiscoveryWorkProducer;
import com.otilm.core.messaging.model.DiscoveryWorkMessage;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.handler.discovery.DiscoveryProcessTickWorker;
import com.otilm.core.service.writer.DiscoveryWriter;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * How a run gets through its processing backlog and how it ends.
 *
 * <p>
 * The import pipeline is mocked at {@link CertificateDiscoveredEventHandler#processBatch}: what matters here is the
 * cursor discipline around it — that each batch claims only unprocessed rows, that a crashed batch is reclaimed rather
 * than skipped, and that the run's final status is read from the evidence its own rows carry. The pipeline's own
 * behaviour has its own tests.
 *
 * <p>
 * Not {@code @Transactional}: the worker commits in its own transactions, so seeded data has to be committed too.
 */
// One row per batch, so the walk through a backlog is observable rather than a single all-at-once pass.
@TestPropertySource(properties = "discovery.processing.batch-size=1")
class DiscoveryProcessTickWorkerITest extends BaseSpringBootTest {

    @MockitoBean
    private CertificateDiscoveredEventHandler importHandler;
    @MockitoBean
    private DiscoveryWorkProducer workProducer;

    @Autowired
    private DiscoveryProcessTickWorker worker;
    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private DiscoveryCertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private DiscoveryWorkRepository workRepository;
    @Autowired
    private DiscoveryWriter discoveryWriter;
    @Autowired
    private DiscoveryWorkWriter workWriter;

    private final List<Integer> claimedBatchSizes = new ArrayList<>();

    // ------------------------------------------------------------------ batching

    @Test
    void backlogLargerThanOneBatch_asksForTheNextBatchItself() throws Exception {
        Discovery run = processingRun();
        stageCertificates(run, 3);
        importsCleanly();

        worker.tick(run.getUuid(), 0);

        assertThat(reload(run).getStatus())
                .as("a batch that leaves work behind must not end the run")
                .isEqualTo(DiscoveryStatus.PROCESSING);
        assertThat(publishedTicks())
                .containsExactly(new DiscoveryWorkMessage(run.getUuid(), DiscoveryWorkType.PROCESS, 0));
        assertThat(agenda(run)).extracting(DiscoveryWork::getWorkType).containsExactly(DiscoveryWorkType.PROCESS);
    }

    @Test
    void repeatedTicks_walkTheBacklogToTheEndWithoutReprocessingARow() throws Exception {
        Discovery run = processingRun();
        stageCertificates(run, 3);
        importsCleanly();

        worker.tick(run.getUuid(), 0);
        worker.tick(run.getUuid(), 0);
        worker.tick(run.getUuid(), 0);

        assertThat(claimedBatchSizes).containsExactly(1, 1, 1);
        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.COMPLETED);
    }

    @Test
    void batchThatDied_isReclaimedByTheNextTickRatherThanSkipped() throws Exception {
        Discovery run = processingRun();
        stageCertificates(run, 2);
        doThrow(new IllegalStateException("pod died mid-batch")).when(importHandler).processBatch(any(), anyList());

        assertThatThrownBy(() -> worker.tick(run.getUuid(), 0)).isInstanceOf(IllegalStateException.class);

        // Nothing stamped its rows, so the backlog is exactly what it was: the cursor is the only progress
        // record, and a batch that never committed made none.
        assertThat(certificateRepository.countByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalse(run.getUuid()))
                .isEqualTo(2);
        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.PROCESSING);
    }

    // ------------------------------------------------------------------ ending the run

    @Test
    void emptyBacklogWithNoFailures_endsTheRunCompleted() throws Exception {
        Discovery run = processingRun();

        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.COMPLETED);
        assertThat(reloaded.getEndTime()).isNotNull();
        assertThat(agenda(run)).isEmpty();
        verify(importHandler, never()).processBatch(any(), anyList());
    }

    @Test
    void rowThatRecordedAReason_endsTheRunAsAWarning() throws Exception {
        Discovery run = processingRun();
        List<DiscoveryCertificate> staged = stageCertificates(run, 2);
        // The pipeline's own bookkeeping stamps the reason; here it is the evidence the worker reads.
        importsCleanly();
        discoveryWriter.markProcessed(List.of(staged.get(0).getUuid()), "Import rolled back: the chain was incomplete");

        worker.tick(run.getUuid(), 0);
        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.WARNING);
        assertThat(reloaded.getMessage()).contains("warnings");
        assertThat(agenda(run)).isEmpty();
    }

    // ------------------------------------------------------------------ ticks with nothing to do

    @Test
    void runThatIsNotProcessing_dropsTheTickWithoutImportingAnything() throws Exception {
        Discovery run = processingRun();
        run.setStatus(DiscoveryStatus.IN_PROGRESS);
        discoveryRepository.saveAndFlush(run);
        stageCertificates(run, 1);

        worker.tick(run.getUuid(), 0);

        verify(importHandler, never()).processBatch(any(), anyList());
        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);
    }

    @Test
    void terminalRun_dropsTheTickAndClearsItsLeftoverAgenda() throws Exception {
        Discovery run = processingRun();
        run.setStatus(DiscoveryStatus.COMPLETED);
        discoveryRepository.saveAndFlush(run);

        worker.tick(run.getUuid(), 0);

        assertThat(agenda(run)).isEmpty();
        verify(importHandler, never()).processBatch(any(), anyList());
    }

    @Test
    void tickForADeletedRun_isDroppedWithoutFailing() {
        worker.tick(UUID.randomUUID(), 0);

        assertThat(workRepository.findAll()).isEmpty();
    }

    // ------------------------------------------------------------------ fixtures

    /** Stubs the pipeline to do what a clean import does: stamp every row of the batch as processed. */
    private void importsCleanly() throws Exception {
        doAnswer(invocation -> {
            List<DiscoveryCertificate> batch = invocation.getArgument(1);
            claimedBatchSizes.add(batch.size());
            discoveryWriter.markProcessed(batch.stream().map(DiscoveryCertificate::getUuid).toList(), null);
            return null;
        }).when(importHandler).processBatch(any(), anyList());
    }

    private List<DiscoveryWorkMessage> publishedTicks() {
        ArgumentCaptor<DiscoveryWorkMessage> published = ArgumentCaptor.forClass(DiscoveryWorkMessage.class);
        verify(workProducer, atLeastOnce()).produceMessage(published.capture());
        return published.getAllValues();
    }

    private List<DiscoveryWork> agenda(Discovery run) {
        return workRepository.findAll().stream().filter(row -> row.getDiscoveryUuid().equals(run.getUuid())).toList();
    }

    private Discovery reload(Discovery run) {
        return discoveryRepository.findByUuid(run.getUuid()).orElseThrow();
    }

    private List<DiscoveryCertificate> stageCertificates(Discovery run, int count) {
        for (int i = 0; i < count; i++) {
            // Each row needs its own content: the column is NOT NULL, and the pipeline groups rows by it.
            CertificateContent content = new CertificateContent();
            content.setFingerprint(UUID.randomUUID().toString());
            content.setContent("staged-" + i);
            DiscoveryCertificate staged = new DiscoveryCertificate();
            staged.setCertificateContent(certificateContentRepository.saveAndFlush(content));
            staged.setDiscovery(run);
            staged.setNewlyDiscovered(true);
            staged.setProcessed(false);
            staged.setCommonName("host-" + i + ".example.com");
            certificateRepository.saveAndFlush(staged);
        }
        return certificateRepository
                .findByDiscoveryUuidAndNewlyDiscoveredTrueAndProcessedFalseOrderByCreatedAsc(run.getUuid(),
                        PageRequest.of(0, 100));
    }

    private Discovery processingRun() {
        Discovery run = new Discovery();
        run.setName("v2-scan-" + UUID.randomUUID());
        run.setKind("IP-HostName");
        run.setStatus(DiscoveryStatus.PROCESSING);
        run.setConnectorStatus(DiscoveryStatus.COMPLETED);
        run.setConnectorUuid(UUID.randomUUID());
        run.setConnectorName("network-discovery");
        run.setConnectorInterfaceUuid(UUID.randomUUID());
        Discovery saved = discoveryRepository.saveAndFlush(run);
        workWriter
                .schedule(saved.getUuid(), DiscoveryWorkType.PROCESS, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        return saved;
    }
}

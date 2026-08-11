package com.otilm.core.integration.service.writer;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.CertificateContent;
import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.entity.DiscoveryHistory;
import com.otilm.core.dao.repository.CertificateContentRepository;
import com.otilm.core.dao.repository.DiscoveryCertificateRepository;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.service.writer.DiscoveryWriter;
import com.otilm.core.util.BaseSpringBootTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveryWriterITest extends BaseSpringBootTest {

    @Autowired
    private DiscoveryWriter discoveryWriter;

    @Autowired
    private DiscoveryRepository discoveryRepository;

    @Autowired
    private DiscoveryCertificateRepository discoveryCertificateRepository;

    @Autowired
    private CertificateContentRepository certificateContentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionHandler transactionHandler;

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Test
    void writerBeanIsASpringProxy() {
        assertThat(AopUtils.isAopProxy(discoveryWriter))
                .as("DiscoveryWriter must be a Spring AOP proxy so @Transactional advice is applied")
                .isTrue();
    }

    /**
     * The writer uses {@code REQUIRED}, as the architecture rule for this package demands, so it composes with whatever
     * transaction the caller provides. Isolation is therefore the caller's job — this documents the half that does not
     * survive, so nobody "simplifies" the orchestrator by dropping the wrapper.
     */
    @Test
    void markProcessedJoinsTheCallerTransactionAndIsLostWhenItRollsBack() {
        DiscoveryCertificate row = givenDiscoveryCertificate(givenDiscovery());

        assertThatThrownBy(() -> newTransaction().executeWithoutResult(status -> {
            discoveryWriter.markProcessed(List.of(row.getUuid()), "Import rolled back: database constraint violation");
            throw new IllegalStateException("caller fails after the writer ran");
        })).isInstanceOf(IllegalStateException.class).hasMessage("caller fails after the writer ran");

        DiscoveryCertificate reloaded = discoveryCertificateRepository.findByUuid(row.getUuid()).orElseThrow();
        assertThat(reloaded.isProcessed())
                .as("a REQUIRED writer joins the caller's transaction, so a doomed caller discards the write")
                .isFalse();
        assertThat(reloaded.getProcessedError()).isNull();
    }

    /**
     * The pattern the orchestrator actually uses: the bookkeeping write runs in its own transaction, opened after the
     * import unit has returned, so it survives that unit rolling back.
     */
    @Test
    void markProcessedSurvivesARollbackWhenTheCallerIsolatesIt() {
        DiscoveryCertificate row = givenDiscoveryCertificate(givenDiscovery());

        assertThatThrownBy(() -> newTransaction().executeWithoutResult(status -> {
            transactionHandler
                    .runInNewTransaction(() -> discoveryWriter
                            .markProcessed(List.of(row.getUuid()),
                                    "Import rolled back: database constraint violation"));
            throw new IllegalStateException("caller fails after the writer ran");
        })).isInstanceOf(IllegalStateException.class).hasMessage("caller fails after the writer ran");

        DiscoveryCertificate reloaded = discoveryCertificateRepository.findByUuid(row.getUuid()).orElseThrow();
        assertThat(reloaded.isProcessed()).isTrue();
        assertThat(reloaded.getProcessedError()).isEqualTo("Import rolled back: database constraint violation");
    }

    @Test
    void markProcessedRecordsACleanOutcomeWithNoReason() {
        DiscoveryCertificate row = givenDiscoveryCertificate(givenDiscovery());

        discoveryWriter.markProcessed(List.of(row.getUuid()), null);

        DiscoveryCertificate reloaded = discoveryCertificateRepository.findByUuid(row.getUuid()).orElseThrow();
        assertThat(reloaded.isProcessed()).isTrue();
        assertThat(reloaded.getProcessedError()).isNull();
    }

    @Test
    void recordProcessedErrorLeavesTheProcessedFlagAlone() {
        DiscoveryCertificate row = givenDiscoveryCertificate(givenDiscovery());
        discoveryWriter.markProcessed(List.of(row.getUuid()), null);

        discoveryWriter
                .recordProcessedError(List.of(row.getUuid()),
                        "Public key could not be associated: the primary key upload failed");

        DiscoveryCertificate reloaded = discoveryCertificateRepository.findByUuid(row.getUuid()).orElseThrow();
        assertThat(reloaded.isProcessed())
                .as("the row finished earlier in the run; only the reason is being added")
                .isTrue();
        assertThat(reloaded.getProcessedError())
                .isEqualTo("Public key could not be associated: the primary key upload failed");
    }

    @Test
    void updateProgressMessageWritesOnlyTheMessage() {
        DiscoveryHistory discovery = givenDiscovery();
        DiscoveryStatus statusBefore = discovery.getStatus();

        discoveryWriter
                .updateProgressMessage(discovery.getUuid(), "Processed 40 % of newly discovered certificates (4 / 10)");

        DiscoveryHistory reloaded = discoveryRepository.findByUuid(discovery.getUuid()).orElseThrow();
        assertThat(reloaded.getMessage()).isEqualTo("Processed 40 % of newly discovered certificates (4 / 10)");
        assertThat(reloaded.getStatus())
                .as("progress reporting must not touch the discovery status")
                .isEqualTo(statusBefore);
    }

    private DiscoveryHistory givenDiscovery() {
        DiscoveryHistory discovery = new DiscoveryHistory();
        discovery.setName("DiscoveryWriterITest-" + UUID.randomUUID());
        discovery.setKind("IP");
        discovery.setStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorUuid(UUID.randomUUID());
        return discoveryRepository.save(discovery);
    }

    private DiscoveryCertificate givenDiscoveryCertificate(DiscoveryHistory discovery) {
        CertificateContent content = new CertificateContent();
        content.setFingerprint(UUID.randomUUID().toString());
        content.setContent("content-" + UUID.randomUUID());
        content = certificateContentRepository.save(content);

        DiscoveryCertificate row = new DiscoveryCertificate();
        row.setDiscovery(discovery);
        row.setDiscoveryUuid(discovery.getUuid());
        row.setCertificateContent(content);
        row.setCertificateContentId(content.getId());
        row.setNewlyDiscovered(true);
        row.setProcessed(false);
        row.setCommonName("writer-itest");
        return discoveryCertificateRepository.save(row);
    }
}

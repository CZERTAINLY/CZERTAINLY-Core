package com.otilm.core.events.handlers;

import com.otilm.core.dao.entity.DiscoveryCertificate;
import com.otilm.core.dao.repository.CertificateRepository;
import com.otilm.core.evaluator.CertificateTriggerEvaluator;
import com.otilm.core.events.handlers.discovery.DiscoveryCertificateOutcome;
import com.otilm.core.events.handlers.discovery.DiscoveryContentGroup;
import com.otilm.core.events.handlers.discovery.DiscoveryRunAccumulator;
import com.otilm.core.events.handlers.discovery.DiscoveryRunContext;
import com.otilm.core.events.transaction.TransactionHandler;
import com.otilm.core.service.writer.DiscoveryWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * The post-processing phases that must survive a failure of the phase before them. A progress write that escapes, or
 * a group the consumption loop never reached, both used to end the run silently — the first by skipping key
 * association altogether, the second by leaving the lost work out of the counts that decide the status.
 */
class CertificateDiscoveredEventHandlerContainmentTest {

    private final DiscoveryWriter discoveryWriter = mock(DiscoveryWriter.class);
    private final CertificateDiscoveredEventHandler handler = new CertificateDiscoveredEventHandler(
            mock(CertificateRepository.class), mock(CertificateTriggerEvaluator.class));

    @BeforeEach
    void setUp() {
        TransactionHandler transactionHandler = mock(TransactionHandler.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(transactionHandler).runInNewTransaction(any(Runnable.class));

        handler.setTransactionHandler(transactionHandler);
        handler.setDiscoveryWriter(discoveryWriter);
    }

    @Test
    void aFailedProgressWriteIsContainedAndCountedRatherThanThrown() {
        doThrow(new RuntimeException("could not reach the database"))
                .when(discoveryWriter).updateProgressMessage(any(UUID.class), anyString());
        DiscoveryRunAccumulator accumulator = new DiscoveryRunAccumulator();

        handler.reportProgressSafely(runContext(), accumulator, 3);

        assertThat(accumulator.counts().bookkeepingFailures())
                .as("progress is cosmetic, but a run whose detail is incomplete is not clean")
                .isEqualTo(1);
    }

    @Test
    void groupsTheLoopNeverReachedAreRecordedAsNotAttempted() {
        DiscoveryRunAccumulator accumulator = new DiscoveryRunAccumulator();
        DiscoveryContentGroup consumed = group(1L, UUID.randomUUID());
        DiscoveryContentGroup unconsumed = group(2L, UUID.randomUUID(), UUID.randomUUID());

        handler.accountForUnconsumedGroups(accumulator, List.of(consumed, unconsumed), Set.of(1L));

        assertThat(accumulator.results())
                .as("only the unconsumed group's rows, and both of them")
                .hasSize(2)
                .allSatisfy(result -> {
                    assertThat(result.outcome()).isEqualTo(DiscoveryCertificateOutcome.NOT_ATTEMPTED);
                    assertThat(result.detail()).isEqualTo("the import did not run to a result");
                });
        assertThat(accumulator.counts().notAttempted())
                .as("one certificate on two hosts is one certificate")
                .isEqualTo(1);
        assertThat(accumulator.counts().allClear())
                .as("a run that lost work must not report itself clean")
                .isFalse();
    }

    @Test
    void aFullyConsumedRunAccountsForNothingExtra() {
        DiscoveryRunAccumulator accumulator = new DiscoveryRunAccumulator();

        handler.accountForUnconsumedGroups(accumulator, List.of(group(1L, UUID.randomUUID())), Set.of(1L));

        assertThat(accumulator.results()).isEmpty();
        assertThat(accumulator.counts().allClear()).isTrue();
    }

    private DiscoveryRunContext runContext() {
        return new DiscoveryRunContext(UUID.randomUUID(), "discovery", UUID.randomUUID(), "connector",
                "kind", UUID.randomUUID(), List.of(), List.of(), UUID.randomUUID(), UUID.randomUUID(), 7, null);
    }

    private DiscoveryContentGroup group(Long contentId, UUID... rowUuids) {
        List<DiscoveryCertificate> rows = Arrays.stream(rowUuids).map(rowUuid -> {
            DiscoveryCertificate row = new DiscoveryCertificate();
            row.setUuid(rowUuid);
            return row;
        }).toList();
        return new DiscoveryContentGroup(contentId, rows);
    }
}

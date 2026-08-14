package com.otilm.core.integration.repository;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryWork;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.dao.repository.DiscoveryWorkRepository;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.util.BaseSpringBootTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL-level coverage for the discovery work agenda: the {@code ON CONFLICT} upsert is idempotent per run and work type,
 * the due query orders soonest-first and excludes the not-yet-due, and deleting a run cascades its agenda rows away at
 * the database level — the sweep must never meet work for a run that no longer exists.
 */
@Transactional
class DiscoveryWorkRepositoryITest extends BaseSpringBootTest {

    // Truncated to what timestamptz can hold, so a stored value reads back equal to what was written.
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private DiscoveryWorkRepository workRepository;
    @Autowired
    private DiscoveryRepository discoveryRepository;

    @Test
    void doubleScheduleKeepsOneRowAndMovesTheDueTime() {
        UUID runUuid = aRun();

        workRepository.schedule(UUID.randomUUID(), runUuid, DiscoveryWorkType.STATUS.name(), NOW.plusMinutes(1));
        workRepository.schedule(UUID.randomUUID(), runUuid, DiscoveryWorkType.STATUS.name(), NOW.plusMinutes(5));

        List<DiscoveryWork> rows = workRepository.findAll();
        assertThat(rows).hasSize(1);
        // Compared as instants: the driver may hand the timestamptz back under a different zone offset.
        assertThat(rows.get(0).getNextDueAt().toInstant()).isEqualTo(NOW.plusMinutes(5).toInstant());
        assertThat(rows.get(0).getAttempt()).isZero();
    }

    @Test
    void dueQueryOrdersSoonestFirstAndExcludesTheNotYetDue() {
        UUID runUuid = aRun();
        workRepository.schedule(UUID.randomUUID(), runUuid, DiscoveryWorkType.PROCESS.name(), NOW.plusHours(2));
        workRepository.schedule(UUID.randomUUID(), runUuid, DiscoveryWorkType.DRAIN.name(), NOW.minusMinutes(1));
        workRepository.schedule(UUID.randomUUID(), runUuid, DiscoveryWorkType.STATUS.name(), NOW.minusMinutes(10));

        List<DiscoveryWork> due = workRepository
                .findByNextDueAtLessThanEqualOrderByNextDueAt(NOW, PageRequest.of(0, 10));

        assertThat(due)
                .extracting(DiscoveryWork::getWorkType)
                .containsExactly(DiscoveryWorkType.STATUS, DiscoveryWorkType.DRAIN);
    }

    @Test
    void deletingTheRunCascadesItsAgendaRows() {
        UUID runUuid = aRun();
        workRepository.schedule(UUID.randomUUID(), runUuid, DiscoveryWorkType.STATUS.name(), NOW);
        workRepository.schedule(UUID.randomUUID(), runUuid, DiscoveryWorkType.DRAIN.name(), NOW);
        assertThat(workRepository.count()).isEqualTo(2);

        discoveryRepository.deleteById(runUuid);
        discoveryRepository.flush();

        assertThat(workRepository.count()).isZero();
    }

    /** A saved, flushed run row — flushed because the native upsert's foreign key reads the table, not the cache. */
    private UUID aRun() {
        Discovery run = new Discovery();
        run.setName("nightly-scan");
        run.setKind("IP-HostName");
        run.setStatus(DiscoveryStatus.IN_PROGRESS);
        run.setConnectorStatus(DiscoveryStatus.IN_PROGRESS);
        run.setConnectorUuid(UUID.randomUUID());
        run.setConnectorName("network-discovery");
        return discoveryRepository.saveAndFlush(run).getUuid();
    }
}

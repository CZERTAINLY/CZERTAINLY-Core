package com.otilm.core.integration.discovery;

import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.ConnectorProblemException;
import com.otilm.api.model.common.error.ErrorCode;
import com.otilm.api.model.common.error.ProblemDetailExtended;
import com.otilm.api.model.connector.discovery.v2.DiscoveryProgressDto;
import com.otilm.api.model.connector.discovery.v2.DiscoveryRunState;
import com.otilm.api.model.connector.discovery.v2.DiscoveryStatusResponseDto;
import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.dao.entity.Discovery;
import com.otilm.core.dao.entity.DiscoveryWork;
import com.otilm.core.dao.repository.DiscoveryRepository;
import com.otilm.core.dao.repository.DiscoveryWorkRepository;
import com.otilm.core.messaging.jms.configuration.DiscoveryWorkProperties;
import com.otilm.core.model.discovery.DiscoveryWorkType;
import com.otilm.core.service.handler.discovery.DiscoveryStatusTickWorker;
import com.otilm.core.service.handler.discovery.DiscoveryV2Client;
import com.otilm.core.service.writer.discovery.DiscoveryWorkWriter;
import com.otilm.core.util.BaseSpringBootTest;
import com.otilm.core.util.DiscoveryRunMetaFixture;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The connector-state mapping, committed against real PostgreSQL. The connector is mocked at the client seam — the
 * point of these tests is what each answer does to the run row and its agenda, not how the call is transported.
 *
 * <p>
 * Not {@code @Transactional}: the worker commits in its own transactions, so seeded data has to be committed too
 * ({@code TestDatabaseCleaner} wipes it between tests).
 */
class DiscoveryStatusTickWorkerITest extends BaseSpringBootTest {

    @MockitoBean
    private DiscoveryV2Client client;

    @Autowired
    private DiscoveryStatusTickWorker worker;
    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private DiscoveryWorkRepository workRepository;
    @Autowired
    private DiscoveryWorkWriter workWriter;
    @Autowired
    private DiscoveryWorkProperties workProperties;

    // ------------------------------------------------------------------ live answers

    @Test
    void runningAnswer_keepsTheRunInProgressAndRefreshesTheBudget() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 40);
        answers(statusResponse(DiscoveryRunState.RUNNING));

        worker.tick(run.getUuid(), 40);

        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);
        assertThat(reload(run).getConnectorState()).isEqualTo("running");
        assertThat(statusRow(run).getAttempt())
                .as("a clear answer pulls the counter back to the ladder's slowest rung, not to zero")
                .isEqualTo(workProperties.scheduleFor(DiscoveryWorkType.STATUS).ceilingAttempt());
    }

    @Test
    void runningAnswer_storesTheProgressSnapshot() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        DiscoveryStatusResponseDto response = statusResponse(DiscoveryRunState.RUNNING);
        DiscoveryProgressDto progress = new DiscoveryProgressDto();
        progress.setProcessed(31L);
        progress.setPhase("scanning");
        response.setProgress(progress);
        answers(response);

        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getProgress().getProcessed()).isEqualTo(31L);
        assertThat(reloaded.getProgress().getPhase()).isEqualTo("scanning");
    }

    @Test
    void stoppedAnswer_pausesTheRunAndStartsTheResumeWindow() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        answers(statusResponse(DiscoveryRunState.STOPPED));

        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.STOPPED);
        assertThat(reloaded.getStoppedAt()).isNotNull();
    }

    @Test
    void repeatedStoppedAnswer_doesNotPushTheResumeDeadlineOut() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.STOPPED);
        OffsetDateTime pausedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
        run.setStoppedAt(pausedAt);
        discoveryRepository.saveAndFlush(run);
        armStatusRow(run, 0);
        answers(statusResponse(DiscoveryRunState.STOPPED));

        worker.tick(run.getUuid(), 0);

        assertThat(reload(run).getStoppedAt())
                .as("the deadline is measured from the original pause, or a paused run could never expire")
                .isCloseTo(pausedAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void runningAnswerAfterAStop_clearsTheResumeWindow() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.STOPPED);
        run.setStoppedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(6));
        discoveryRepository.saveAndFlush(run);
        armStatusRow(run, 0);
        answers(statusResponse(DiscoveryRunState.RUNNING));

        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);
        assertThat(reloaded.getStoppedAt())
                .as("a resumed run carries no resume deadline; a stale one would expire its next pause on arrival")
                .isNull();
    }

    @Test
    void completedAnswer_leavesTheRunInProgressAndArmsTheDrain() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        answers(statusResponse(DiscoveryRunState.COMPLETED));

        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus())
                .as("the connector is done, but Core still owns the tail drain")
                .isEqualTo(DiscoveryStatus.IN_PROGRESS);
        assertThat(reloaded.getConnectorState()).isEqualTo("completed");
        assertThat(agenda(run))
                .extracting(DiscoveryWork::getWorkType)
                .containsExactlyInAnyOrder(DiscoveryWorkType.STATUS, DiscoveryWorkType.DRAIN);
    }

    // ------------------------------------------------------------------ terminal answers

    @Test
    void failedAnswer_endsTheRunAndReleasesItsAgendaAndHandle() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        answers(statusResponse(DiscoveryRunState.FAILED));

        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.FAILED);
        assertThat(reloaded.getEndTime()).isNotNull();
        assertThat(reloaded.getRunMeta()).isNull();
        assertThat(agenda(run)).isEmpty();
    }

    @Test
    void cancelledAnswer_endsTheRunAsCancelled() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        answers(statusResponse(DiscoveryRunState.CANCELLED));

        worker.tick(run.getUuid(), 0);

        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.CANCELLED);
        assertThat(agenda(run)).isEmpty();
    }

    // ------------------------------------------------------------------ unanswered ticks

    @Test
    void runTheConnectorNoLongerTracks_endsFailedWithoutWaitingOutTheBudget() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        when(client.status(any())).thenThrow(notFound());

        worker.tick(run.getUuid(), 0);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.FAILED);
        assertThat(reloaded.getMessage()).contains("no longer tracks");
        assertThat(agenda(run)).isEmpty();
    }

    @Test
    void transientFailureWithBudgetLeft_leavesTheRunAndItsAgendaAlone() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 3);
        when(client.status(any())).thenThrow(new ConnectorException("connection refused"));

        worker.tick(run.getUuid(), 3);

        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);
        assertThat(agenda(run)).as("the row stays: the claimer already pushed it up the backoff ladder").hasSize(1);
        assertThat(statusRow(run).getAttempt())
                .as("a failed tick must not refresh the budget it is spending")
                .isEqualTo(3);
    }

    @Test
    void lastAllowedAttempt_endsTheRunFailed() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        when(client.status(any())).thenThrow(new ConnectorException("connection refused"));
        int lastAttempt = workProperties.scheduleFor(DiscoveryWorkType.STATUS).maxAttempts() - 1;

        worker.tick(run.getUuid(), lastAttempt);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.FAILED);
        assertThat(reloaded.getMessage()).contains("stopped answering");
        assertThat(agenda(run)).isEmpty();
    }

    @Test
    void answerWithoutARunState_isNotTreatedAsAnAnswer() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 3);
        DiscoveryStatusResponseDto stateless = new DiscoveryStatusResponseDto();
        stateless.setHighestSequence(0L);
        answers(stateless);

        worker.tick(run.getUuid(), 3);

        // The state is required on the wire, so its absence is not an answer. Reading it as one would put the
        // null through state.getCode() inside the transaction, where it escapes the connector-call catch and is
        // merely acknowledged -- and the tick then retries past its budget forever.
        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.IN_PROGRESS);
        assertThat(statusRow(run).getAttempt())
                .as("a non-answer must not refresh the budget it is spending")
                .isEqualTo(3);
    }

    @Test
    void connectorThatKeepsOmittingTheRunState_endsTheRun() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.IN_PROGRESS);
        armStatusRow(run, 0);
        answers(new DiscoveryStatusResponseDto());
        int lastAttempt = workProperties.scheduleFor(DiscoveryWorkType.STATUS).maxAttempts() - 1;

        worker.tick(run.getUuid(), lastAttempt);

        Discovery reloaded = reload(run);
        assertThat(reloaded.getStatus()).isEqualTo(DiscoveryStatus.FAILED);
        assertThat(reloaded.getMessage()).contains("omitted the run state");
        assertThat(agenda(run)).isEmpty();
    }

    // ------------------------------------------------------------------ ticks with nothing to do

    @Test
    void terminalRun_dropsTheTickAndClearsAnyLeftoverAgenda() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.COMPLETED);
        armStatusRow(run, 0);

        worker.tick(run.getUuid(), 0);

        assertThat(agenda(run)).isEmpty();
        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.COMPLETED);
    }

    @Test
    void runHandedOverToProcessing_dropsTheTickWithoutCallingTheConnector() throws Exception {
        Discovery run = v2Run(DiscoveryStatus.PROCESSING);
        run.setRunMeta(null);
        discoveryRepository.saveAndFlush(run);
        armStatusRow(run, 0);
        workWriter.schedule(run.getUuid(), DiscoveryWorkType.PROCESS, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

        worker.tick(run.getUuid(), 0);

        // The swap released the connector handle. Calling status here would read the resulting 404 as "the run
        // vanished" and end a healthy import as FAILED.
        verify(client, never()).status(any());
        assertThat(reload(run).getStatus()).isEqualTo(DiscoveryStatus.PROCESSING);
        assertThat(agenda(run))
                .as("only the obsolete STATUS row goes; the PROCESS row still drives the import")
                .extracting(DiscoveryWork::getWorkType)
                .containsExactly(DiscoveryWorkType.PROCESS);
    }

    @Test
    void tickForADeletedRun_isDroppedWithoutFailing() {
        worker.tick(UUID.randomUUID(), 0);

        assertThat(workRepository.findAll()).isEmpty();
    }

    // ------------------------------------------------------------------ fixtures

    private void answers(DiscoveryStatusResponseDto response) throws Exception {
        when(client.status(any())).thenReturn(response);
    }

    private static DiscoveryStatusResponseDto statusResponse(DiscoveryRunState state) {
        DiscoveryStatusResponseDto response = new DiscoveryStatusResponseDto();
        response.setState(state);
        response.setHighestSequence(0L);
        return response;
    }

    private static ConnectorProblemException notFound() {
        return new ConnectorProblemException(ProblemDetailExtended
                .fromErrorCode(ErrorCode.OPERATION_NOT_TRACKED, "unknown run", URI.create("https://example.com"),
                        null));
    }

    private void armStatusRow(Discovery run, int attempt) {
        workWriter.schedule(run.getUuid(), DiscoveryWorkType.STATUS, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        workWriter
                .reschedule(run.getUuid(), DiscoveryWorkType.STATUS, attempt,
                        OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
    }

    private DiscoveryWork statusRow(Discovery run) {
        return agenda(run)
                .stream()
                .filter(row -> row.getWorkType() == DiscoveryWorkType.STATUS)
                .findFirst()
                .orElseThrow();
    }

    private List<DiscoveryWork> agenda(Discovery run) {
        return workRepository.findAll().stream().filter(row -> row.getDiscoveryUuid().equals(run.getUuid())).toList();
    }

    private Discovery reload(Discovery run) {
        Optional<Discovery> reloaded = discoveryRepository.findByUuid(run.getUuid());
        return reloaded.orElseThrow();
    }

    private Discovery v2Run(DiscoveryStatus status) {
        Discovery run = new Discovery();
        run.setName("v2-scan-" + UUID.randomUUID());
        run.setKind("IP-HostName");
        run.setStatus(status);
        run.setConnectorStatus(status);
        run.setConnectorUuid(UUID.randomUUID());
        run.setConnectorName("network-discovery");
        run.setConnectorInterfaceUuid(UUID.randomUUID());
        run.setRunMeta(DiscoveryRunMetaFixture.runMeta("connectorRunId", "run-42"));
        return discoveryRepository.saveAndFlush(run);
    }
}

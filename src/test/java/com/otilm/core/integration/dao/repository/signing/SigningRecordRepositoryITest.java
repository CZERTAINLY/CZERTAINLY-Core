package com.otilm.core.integration.dao.repository.signing;

import com.otilm.api.model.client.signing.profile.scheme.SigningScheme;
import com.otilm.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.otilm.api.model.core.signing.SigningProtocol;
import com.otilm.core.dao.entity.signing.SigningProfile;
import com.otilm.core.dao.entity.signing.SigningProfileVersion;
import com.otilm.core.dao.entity.signing.SigningRecord;
import com.otilm.core.dao.entity.signing.SigningRecordVolume;
import com.otilm.core.dao.repository.signing.SigningProfileRepository;
import com.otilm.core.dao.repository.signing.SigningProfileVersionRepository;
import com.otilm.core.dao.repository.signing.SigningRecordRepository;
import com.otilm.core.dao.repository.signing.SigningRecordVolumeRepository;
import com.otilm.core.util.BaseSpringBootTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigningRecordRepositoryITest extends BaseSpringBootTest {

    private static final int BATCH_LIMIT_LARGER_THAN_FIXTURES = 1000;
    private static final Duration BLOCKED_DELETE_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    @Autowired
    private SigningRecordRepository repository;

    @Autowired
    private SigningRecordVolumeRepository volumeRepository;

    @Autowired
    private SigningProfileRepository signingProfileRepository;

    @Autowired
    private SigningProfileVersionRepository signingProfileVersionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource testDataSource;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Wraps the {@code @Modifying} native delete queries, which require an active transaction. Fixtures are committed
     * outside it, mirroring the retention sweeper running the delete over already-persisted data.
     */
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void initTransactionTemplate() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs a {@code @Modifying} delete (which requires an active transaction) in its own short transaction over the
     * already-committed fixtures, returning the affected row count as a primitive.
     */
    private int doInTransaction(IntSupplier delete) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> delete.getAsInt()));
    }

    @Test
    void existsBySigningProfileUuidAndSigningProfileVersion_trueWhenRecordExists() {
        // given
        var version = 3;
        SigningProfile profile = insertProfile("exists-match");
        insertProfileVersion(profile, version);
        insertRecord(profile, version);

        // when
        boolean exists = repository.existsBySigningProfileUuidAndSigningProfileVersion(profile.getUuid(), version);

        // then
        assertTrue(exists);
    }

    @Test
    void existsBySigningProfileUuidAndSigningProfileVersion_falseForDifferentVersion() {
        // given
        var recordedVersion = 1;
        var queriedVersion = 2;
        SigningProfile profile = insertProfile("exists-other-version");
        insertProfileVersion(profile, recordedVersion);
        insertRecord(profile, recordedVersion);

        // when
        boolean exists = repository
                .existsBySigningProfileUuidAndSigningProfileVersion(profile.getUuid(), queriedVersion);

        // then
        assertFalse(exists);
    }

    @Test
    void protocol_roundTripsThroughThePersistedRecord() {
        // given
        SigningProfile profile = insertProfile("protocol-roundtrip");
        insertProfileVersion(profile, 1);
        SigningRecord signingRecord = insertRecordWithProtocol(profile, SigningProtocol.CSC_API);

        // when
        SigningRecord reloaded = repository.findById(signingRecord.getUuid()).orElseThrow();

        // then
        assertEquals(SigningProtocol.CSC_API, reloaded.getProtocol());
    }

    @Test
    void deleteExpiredByRetention_deletesRecordsOlderThanRetentionWindow() {
        // given
        var retentionDays = 7;
        var beforeRetentionWindow = Instant.now().minus(Duration.ofDays(10));
        SigningProfile profile = insertProfile("retention-expired");
        insertProfileVersion(profile, 1, retentionDays, false);
        SigningRecord expired = insertRecordSignedAt(profile, 1, beforeRetentionWindow);

        // when
        int deleted = doInTransaction(() -> repository.deleteExpiredByRetention(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(1, deleted);
        assertFalse(repository.existsById(expired.getUuid()));
    }

    @Test
    void deleteExpiredByRetention_keepsRecordsWithinRetentionWindow() {
        // given
        var retentionDays = 30;
        var withinRetentionWindow = Instant.now().minus(Duration.ofDays(5));
        SigningProfile profile = insertProfile("retention-within");
        insertProfileVersion(profile, 1, retentionDays, false);
        SigningRecord fresh = insertRecordSignedAt(profile, 1, withinRetentionWindow);

        // when
        int deleted = doInTransaction(() -> repository.deleteExpiredByRetention(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(0, deleted);
        assertTrue(repository.existsById(fresh.getUuid()));
    }

    @Test
    void deleteExpiredByRetention_keepsRecordsOfProfilesWithoutRetention() {
        // given
        var noRetention = (Integer) null;
        var createdLongAgo = Instant.now().minus(Duration.ofDays(1000));
        SigningProfile profile = insertProfile("retention-disabled");
        insertProfileVersion(profile, 1, noRetention, false);
        SigningRecord old = insertRecordSignedAt(profile, 1, createdLongAgo);

        // when
        int deleted = doInTransaction(() -> repository.deleteExpiredByRetention(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(0, deleted);
        assertTrue(repository.existsById(old.getUuid()));
    }

    @Test
    void deleteRetrievedAndFlagged_deletesRetrievedRecordsOfFlaggedProfiles() {
        // given
        var retrievedAt = Instant.now();
        SigningProfile profile = insertProfile("flagged-retrieved");
        insertProfileVersion(profile, 1, null, true);
        SigningRecord retrieved = insertRecord(profile, 1, retrievedAt);

        // when
        int deleted = doInTransaction(() -> repository.deleteRetrievedAndFlagged(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(1, deleted);
        assertFalse(repository.existsById(retrieved.getUuid()));
    }

    @Test
    void deleteRetrievedAndFlagged_keepsRetrievedRecordsWhenProfileNotFlagged() {
        // given
        var retrievedAt = Instant.now();
        SigningProfile profile = insertProfile("not-flagged-retrieved");
        insertProfileVersion(profile, 1);
        SigningRecord retrieved = insertRecord(profile, 1, retrievedAt);

        // when
        int deleted = doInTransaction(() -> repository.deleteRetrievedAndFlagged(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(0, deleted);
        assertTrue(repository.existsById(retrieved.getUuid()));
    }

    @Test
    void deleteRetrievedAndFlagged_keepsNotYetRetrievedRecordsOfFlaggedProfiles() {
        // given
        var notRetrieved = (Instant) null;
        SigningProfile profile = insertProfile("flagged-not-retrieved");
        insertProfileVersion(profile, 1, null, true);
        SigningRecord pending = insertRecord(profile, 1, notRetrieved);

        // when
        int deleted = doInTransaction(() -> repository.deleteRetrievedAndFlagged(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(0, deleted);
        assertTrue(repository.existsById(pending.getUuid()));
    }

    @Test
    void deleteByUuid_rollsTheRecordIntoItsHourlyBucket() {
        // given
        var signedAt = Instant.parse("2026-03-01T12:34:56Z");
        SigningProfile profile = insertProfile("rollup-single");
        insertProfileVersion(profile, 1);
        SigningRecord signingRecord = insertRecordSignedAt(profile, 1, signedAt);

        // when
        int deleted = doInTransaction(() -> repository.deleteByUuid(signingRecord.getUuid()));

        // then
        assertEquals(1, deleted);
        assertFalse(repository.existsById(signingRecord.getUuid()));
        assertEquals(1, countIn(profile, "2026-03-01T12:00:00Z"));
    }

    @Test
    void deleteByUuid_accumulatesRecordsSignedInTheSameHour() {
        // given
        SigningProfile profile = insertProfile("rollup-accumulate");
        insertProfileVersion(profile, 1);
        SigningRecord first = insertRecordSignedAt(profile, 1, Instant.parse("2026-03-01T12:00:00Z"));
        SigningRecord second = insertRecordSignedAt(profile, 1, Instant.parse("2026-03-01T12:59:59Z"));

        // when
        doInTransaction(() -> repository.deleteByUuid(first.getUuid()));
        doInTransaction(() -> repository.deleteByUuid(second.getUuid()));

        // then
        assertEquals(1, volumeRepository.count());
        assertEquals(2, countIn(profile, "2026-03-01T12:00:00Z"));
    }

    @Test
    void deleteByUuid_keepsSeparateBucketsPerHourAndProfile() {
        // given
        SigningProfile profile = insertProfile("rollup-split");
        SigningProfile otherProfile = insertProfile("rollup-split-other");
        insertProfileVersion(profile, 1);
        insertProfileVersion(otherProfile, 1);
        SigningRecord noon = insertRecordSignedAt(profile, 1, Instant.parse("2026-03-01T12:10:00Z"));
        SigningRecord onePm = insertRecordSignedAt(profile, 1, Instant.parse("2026-03-01T13:10:00Z"));
        SigningRecord elsewhere = insertRecordSignedAt(otherProfile, 1, Instant.parse("2026-03-01T12:20:00Z"));

        // when
        doInTransaction(() -> repository.deleteByUuid(noon.getUuid()));
        doInTransaction(() -> repository.deleteByUuid(onePm.getUuid()));
        doInTransaction(() -> repository.deleteByUuid(elsewhere.getUuid()));

        // then
        assertEquals(1, countIn(profile, "2026-03-01T12:00:00Z"));
        assertEquals(1, countIn(profile, "2026-03-01T13:00:00Z"));
        assertEquals(1, countIn(otherProfile, "2026-03-01T12:00:00Z"));
    }

    @Test
    void deleteExpiredByRetention_rollsUpEveryRecordItRemoves() {
        // given
        var retentionDays = 7;
        var expiredHour = Instant.now().minus(Duration.ofDays(10)).truncatedTo(ChronoUnit.HOURS);
        SigningProfile profile = insertProfile("rollup-retention");
        insertProfileVersion(profile, 1, retentionDays, false);
        insertRecordSignedAt(profile, 1, expiredHour);
        insertRecordSignedAt(profile, 1, expiredHour.plus(Duration.ofMinutes(30)));

        // when
        int deleted = doInTransaction(() -> repository.deleteExpiredByRetention(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(2, deleted);
        assertEquals(2, countIn(profile, expiredHour));
    }

    @Test
    void deleteExpiredByRetention_rollsUpOnlyTheRecordsWithinTheBatchLimit() {
        // given
        var oneOfTwo = 1;
        var retentionDays = 7;
        var expiredHour = Instant.now().minus(Duration.ofDays(10)).truncatedTo(ChronoUnit.HOURS);
        SigningProfile profile = insertProfile("rollup-retention-limit");
        insertProfileVersion(profile, 1, retentionDays, false);
        insertRecordSignedAt(profile, 1, expiredHour);
        insertRecordSignedAt(profile, 1, expiredHour);

        // when
        int deleted = doInTransaction(() -> repository.deleteExpiredByRetention(oneOfTwo));

        // then
        assertEquals(oneOfTwo, deleted);
        assertEquals(oneOfTwo, countIn(profile, expiredHour));
        assertEquals(1, repository.count());
    }

    @Test
    void deleteRetrievedAndFlagged_rollsUpEveryRecordItRemoves() {
        // given
        var signedAt = Instant.parse("2026-04-02T08:15:00Z");
        SigningProfile profile = insertProfile("rollup-retrieved");
        insertProfileVersion(profile, 1, null, true);
        insertRecord(profile, 1, Instant.now(), signedAt);

        // when
        int deleted = doInTransaction(() -> repository.deleteRetrievedAndFlagged(BATCH_LIMIT_LARGER_THAN_FIXTURES));

        // then
        assertEquals(1, deleted);
        assertEquals(1, countIn(profile, "2026-04-02T08:00:00Z"));
    }

    @Test
    void deleteByUuid_countsTheSigningOnceWhenAnotherDeletePathRemovesTheRecordFirst() throws Exception {
        // given
        SigningProfile profile = insertProfile("rollup-contested");
        insertProfileVersion(profile, 1);
        SigningRecord contested = insertRecordSignedAt(profile, 1, Instant.parse("2026-05-01T09:30:00Z"));

        // when
        int deleted = deleteBehindAnInFlightDelete(contested);

        // then
        assertEquals(0, deleted);
        assertFalse(repository.existsById(contested.getUuid()));
        assertEquals(0, volumeRepository.count());
    }

    /**
     * Runs the keyed delete against a record another connection has already deleted but not yet committed, releasing
     * that connection only once the keyed delete is waiting on the row. The competing delete does not roll anything up,
     * so a statement that rolls up rows it did not remove shows up here as a bucket that should not exist.
     */
    private int deleteBehindAnInFlightDelete(SigningRecord signingRecord) throws Exception {
        ExecutorService waiting = Executors.newSingleThreadExecutor();
        try (Connection inFlight = testDataSource.getConnection()) {
            inFlight.setAutoCommit(false);
            try (PreparedStatement delete = inFlight.prepareStatement("DELETE FROM signing_record WHERE uuid = ?")) {
                delete.setObject(1, signingRecord.getUuid());
                delete.executeUpdate();
            }
            Future<Integer> blocked = waiting
                    .submit(() -> doInTransaction(() -> repository.deleteByUuid(signingRecord.getUuid())));
            awaitBackendWaitingOnALock();
            inFlight.commit();
            return blocked.get(BLOCKED_DELETE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            waiting.shutdownNow();
        }
    }

    /**
     * Polls until a backend is blocked on a lock, so the delete under test is known to be waiting before the competing
     * transaction commits. Gives up quietly at the bound: the assertions hold either way, this only makes the
     * interleaving the test is after the one that actually happens.
     */
    private void awaitBackendWaitingOnALock() throws SQLException, InterruptedException {
        Instant deadline = Instant.now().plus(BLOCKED_DELETE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            try (Connection probe = testDataSource.getConnection();
                    PreparedStatement waiting = probe
                            .prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'");
                    ResultSet result = waiting.executeQuery()) {
                if (result.next() && result.getInt(1) > 0) {
                    return;
                }
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
    }

    @Test
    void deleteExpiredByRetention_leavesAContendedRecordToTheStatementHoldingIt() throws SQLException {
        // given
        var retentionDays = 7;
        var expiredHour = Instant.now().minus(Duration.ofDays(10)).truncatedTo(ChronoUnit.HOURS);
        SigningProfile profile = insertProfile("rollup-retention-contended");
        insertProfileVersion(profile, 1, retentionDays, false);
        SigningRecord contested = insertRecordSignedAt(profile, 1, expiredHour);

        // when
        int deleted = whileHeldElsewhere(contested,
                () -> doInTransaction(() -> repository.deleteExpiredByRetention(BATCH_LIMIT_LARGER_THAN_FIXTURES)));

        // then
        assertEquals(0, deleted);
        assertEquals(0, volumeRepository.count());
        assertTrue(repository.existsById(contested.getUuid()));
    }

    /**
     * Runs {@code sweep} while a second connection holds {@code signingRecord} under {@code FOR UPDATE}. A batch that
     * waited for contended rows instead of skipping them would block here until the test's own claim is released, which
     * only happens afterwards.
     */
    private int whileHeldElsewhere(SigningRecord signingRecord, IntSupplier sweep) throws SQLException {
        try (Connection holder = testDataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement select = holder
                    .prepareStatement("SELECT uuid FROM signing_record WHERE uuid = ? FOR UPDATE")) {
                select.setObject(1, signingRecord.getUuid());
                select.executeQuery().close();
            }
            try {
                return Objects.requireNonNull(transactionTemplate.execute(status -> {
                    entityManager.createNativeQuery("SET LOCAL lock_timeout = '5s'").executeUpdate();
                    return sweep.getAsInt();
                }));
            } finally {
                holder.rollback();
            }
        }
    }

    @Test
    void deleteQueries_recordNothingWhenTheyRemoveNothing() {
        // given
        var withinRetentionWindow = Instant.now();
        SigningProfile profile = insertProfile("rollup-noop");
        insertProfileVersion(profile, 1, 30, false);
        insertRecordSignedAt(profile, 1, withinRetentionWindow);

        // when
        doInTransaction(() -> repository.deleteExpiredByRetention(BATCH_LIMIT_LARGER_THAN_FIXTURES));
        doInTransaction(() -> repository.deleteRetrievedAndFlagged(BATCH_LIMIT_LARGER_THAN_FIXTURES));
        doInTransaction(() -> repository.deleteByUuid(UUID.randomUUID()));

        // then
        assertEquals(0, volumeRepository.count());
    }

    private long countIn(SigningProfile profile, String bucketStart) {
        return countIn(profile, Instant.parse(bucketStart));
    }

    private long countIn(SigningProfile profile, Instant bucketStart) {
        return volumeRepository
                .findAll()
                .stream()
                .filter(volume -> volume.getSigningProfileUuid().equals(profile.getUuid()))
                .filter(volume -> volume.getBucketStart().equals(bucketStart))
                .mapToLong(SigningRecordVolume::getSigningCount)
                .sum();
    }

    private SigningProfile insertProfile(String name) {
        SigningProfile profile = new SigningProfile();
        profile.setName(name);
        profile.setEnabled(false);
        profile.setSigningScheme(SigningScheme.DELEGATED);
        profile.setWorkflowType(SigningWorkflowType.RAW_SIGNING);
        profile.setLatestVersion(1);
        return signingProfileRepository.saveAndFlush(profile);
    }

    private SigningRecord insertRecord(SigningProfile profile, int version) {
        return insertRecord(profile, version, null, Instant.now());
    }

    private SigningRecord insertRecord(SigningProfile profile, int version, Instant signedDocumentRetrievedAt) {
        return insertRecord(profile, version, signedDocumentRetrievedAt, Instant.now());
    }

    private SigningRecord insertRecordSignedAt(SigningProfile profile, int version, Instant createdAt) {
        return insertRecord(profile, version, null, createdAt);
    }

    private SigningRecord insertRecord(SigningProfile profile, int version, Instant signedDocumentRetrievedAt,
            Instant signingTime) {
        SigningRecord signingRecord = new SigningRecord();
        signingRecord.setSigningProfileUuid(profile.getUuid());
        signingRecord.setSigningProfileVersion(version);
        signingRecord.setProtocol(SigningProtocol.TSP);
        signingRecord.setSigningTime(signingTime);
        signingRecord.setSignedDocumentRetrievedAt(signedDocumentRetrievedAt);
        return repository.saveAndFlush(signingRecord);
    }

    private SigningRecord insertRecordWithProtocol(SigningProfile profile, SigningProtocol protocol) {
        SigningRecord signingRecord = new SigningRecord();
        signingRecord.setSigningProfileUuid(profile.getUuid());
        signingRecord.setSigningProfileVersion(1);
        signingRecord.setProtocol(protocol);
        signingRecord.setSigningTime(Instant.parse("2026-03-01T12:00:00Z"));
        return repository.saveAndFlush(signingRecord);
    }

    private void insertProfileVersion(SigningProfile profile, int version) {
        insertProfileVersion(profile, version, null, false);
    }

    /**
     * Persists the version row a record references by int, carrying the versioned retention / delete-after-retrieval
     * policy the sweep queries now join on, so the fixtures stay valid should the
     * {@code (signing_profile_uuid, signing_profile_version)} reference ever become a hard FK.
     */
    private void insertProfileVersion(SigningProfile profile, int version, Integer retentionDays,
            boolean deleteAfterRetrieval) {
        SigningProfileVersion profileVersion = new SigningProfileVersion();
        profileVersion.setSigningProfile(profile);
        profileVersion.setVersion(version);
        profileVersion.setSigningScheme(SigningScheme.DELEGATED);
        profileVersion.setWorkflowType(SigningWorkflowType.RAW_SIGNING);
        profileVersion.setRetentionDays(retentionDays);
        profileVersion.setDeleteAfterRetrieval(deleteAfterRetrieval);
        signingProfileVersionRepository.saveAndFlush(profileVersion);
    }
}

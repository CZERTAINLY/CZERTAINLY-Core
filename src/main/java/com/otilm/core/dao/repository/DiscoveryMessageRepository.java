package com.otilm.core.dao.repository;

import com.otilm.api.model.core.discovery.DiscoveryMessageSeverity;
import com.otilm.core.dao.entity.DiscoveryMessage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscoveryMessageRepository extends JpaRepository<DiscoveryMessage, Long> {

    /**
     * Records one occurrence group of a problem, aggregating onto the row already carrying it.
     *
     * <p>
     * No lock is taken anywhere: a concurrent append of the same problem loses the race to the unique index and lands
     * in the {@code DO UPDATE}, so the two counts add rather than one overwriting the other. The bound is a guard on
     * new rows only — a repeat always lands, however many rows the run already has, because aggregating onto an
     * existing row grows nothing.
     *
     * @return 1 when the message was recorded, 0 when a bound refused it a row of its own
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}discovery_message AS m
                (discovery_uuid, severity, code, message, occurrences, first_seen_at, last_seen_at)
            SELECT CAST(:discoveryUuid AS UUID), CAST(:severity AS VARCHAR), CAST(:code AS VARCHAR),
                   CAST(:message AS VARCHAR), CAST(:occurrences AS BIGINT), now(), now()
            WHERE EXISTS (SELECT 1 FROM {h-schema}discovery_message
                          WHERE discovery_uuid = CAST(:discoveryUuid AS UUID)
                            AND code = CAST(:code AS VARCHAR)
                            AND message_hash = md5(CAST(:message AS TEXT)))
               OR ((SELECT count(*) FROM {h-schema}discovery_message
                    WHERE discovery_uuid = CAST(:discoveryUuid AS UUID)
                      AND code = CAST(:code AS VARCHAR)) < :maxPerCode
                   AND (SELECT count(*) FROM {h-schema}discovery_message
                        WHERE discovery_uuid = CAST(:discoveryUuid AS UUID)) < :maxPerRun)
            ON CONFLICT (discovery_uuid, code, message_hash) DO UPDATE
                SET occurrences = m.occurrences + EXCLUDED.occurrences, last_seen_at = EXCLUDED.last_seen_at
            """, nativeQuery = true)
    int appendWithinBounds(@Param("discoveryUuid") UUID discoveryUuid, @Param("severity") String severity,
            @Param("code") String code, @Param("message") String message, @Param("occurrences") long occurrences,
            @Param("maxPerCode") int maxPerCode, @Param("maxPerRun") int maxPerRun);

    /**
     * As {@link #appendWithinBounds}, for the two messages that must land whatever the run has already collected: the
     * reason a run ended, and the row saying that other messages were dropped.
     */
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}discovery_message AS m
                (discovery_uuid, severity, code, message, occurrences, first_seen_at, last_seen_at)
            VALUES (CAST(:discoveryUuid AS UUID), CAST(:severity AS VARCHAR), CAST(:code AS VARCHAR),
                    CAST(:message AS VARCHAR), CAST(:occurrences AS BIGINT), now(), now())
            ON CONFLICT (discovery_uuid, code, message_hash) DO UPDATE
                SET occurrences = m.occurrences + EXCLUDED.occurrences, last_seen_at = EXCLUDED.last_seen_at
            """, nativeQuery = true)
    void append(@Param("discoveryUuid") UUID discoveryUuid, @Param("severity") String severity,
            @Param("code") String code, @Param("message") String message, @Param("occurrences") long occurrences);

    /**
     * Whether the run collected anything at or above a given severity — what the terminal decision asks, in place of
     * the old "is the log non-empty", which downgraded a run over a problem that had since been recovered.
     */
    boolean existsByDiscoveryUuidAndSeverityIn(UUID discoveryUuid, Collection<DiscoveryMessageSeverity> severities);

    /** The run's log, oldest first. Ordered by the identity column; see {@code DiscoveryMessage.id}. */
    List<DiscoveryMessage> findByDiscoveryUuidOrderByIdAsc(UUID discoveryUuid);

    long countByDiscoveryUuid(UUID discoveryUuid);
}

package com.otilm.core.service.writer.cbom;

import com.otilm.core.dao.repository.cbom.CbomTombstoneRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records that a CBOM was deleted.
 *
 * <p>
 * {@code REQUIRED}, so the tombstone commits with the deletion it describes or with neither. A tombstone written by a
 * transaction that then rolled back would claim a deletion that never happened, and a deletion without a tombstone
 * leaves the next sync free to re-ingest the document.
 */
@Service
public class CbomTombstoneWriter {

    private final CbomTombstoneRepository tombstoneRepository;

    public CbomTombstoneWriter(CbomTombstoneRepository tombstoneRepository) {
        this.tombstoneRepository = tombstoneRepository;
    }

    /**
     * @param cbomUuid the deleted CBOM's own uuid, which is the tombstone's primary key -- so a retried deletion
     * records nothing new instead of failing
     */
    @Transactional
    public void record(UUID cbomUuid, String serialNumber, int version, OffsetDateTime deletedAt, String deletedBy) {
        tombstoneRepository.recordTombstone(cbomUuid, serialNumber, version, deletedAt, deletedBy);
    }
}

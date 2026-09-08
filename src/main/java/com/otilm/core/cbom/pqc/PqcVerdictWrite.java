package com.otilm.core.cbom.pqc;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row's verdict, ready for the batch writer.
 *
 * @param updatedAsRead the row's {@code i_upd} when its inputs were read; the write is refused if it has moved since
 */
public record PqcVerdictWrite(UUID assetUuid, OffsetDateTime updatedAsRead, PqcDecision decision) {
}

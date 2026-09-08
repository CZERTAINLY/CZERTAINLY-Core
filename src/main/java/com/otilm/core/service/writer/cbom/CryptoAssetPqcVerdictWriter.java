package com.otilm.core.service.writer.cbom;

import com.otilm.core.cbom.asset.JsonColumnText;
import com.otilm.core.cbom.pqc.PqcVerdictWrite;
import com.otilm.core.dao.repository.cbom.CryptoAssetRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sweep's batch write: one short transaction per batch.
 *
 * <p>
 * A separate bean because two rules forbid putting this on {@code CryptoAssetWriter}.
 * {@code IdentityRulesetStampArchTest} fails if any production class depends on that bean -- it is the tripwire that
 * forces {@code IdentityRuleset.VERSION} to move when ingest is wired. {@code TransactionalBoundaryArchTest} Rule D
 * requires every public method of a {@code @Service} here to be {@code REQUIRED}. {@code SigningRecordWriter} escapes
 * Rule D only by being a {@code @Component}, so this is one too.
 *
 * <p>
 * {@code REQUIRES_NEW} because a {@code REQUIRED} write would join the sweep's lock transaction and hold every row lock
 * until the sweep ended.
 */
@Component
public class CryptoAssetPqcVerdictWriter {

    private final CryptoAssetRepository assetRepository;

    public CryptoAssetPqcVerdictWriter(CryptoAssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /**
     * @return rows actually written; below {@code batch.size()} when ingest touched a row after the read, which is
     * normal
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int applyStaleBatch(List<PqcVerdictWrite> batch, int rulesetVersion) {
        int written = 0;
        for (PqcVerdictWrite write : batch) {
            written += assetRepository
                    .applyPqcVerdictIfStale(write.assetUuid(), write.updatedAsRead(), write.decision().verdict().name(),
                            write.decision().ruleId(), write.decision().reason(), rulesetVersion,
                            JsonColumnText.render(write.decision().evaluatedFields()));
        }
        return written;
    }
}

package com.otilm.core.tasks;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.scheduler.SchedulerJobExecutionStatus;
import com.otilm.core.api.ScheduledJobSkippedException;
import com.otilm.core.cbom.pqc.PqcVerdictSweeper;
import com.otilm.core.model.ScheduledTaskResult;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The sweep's schedule, and nothing else.
 *
 * <p>
 * A system {@code ScheduledJobTask} rather than {@code @Scheduled}, which reaches nobody: under SaaS an operator cannot
 * touch yml. Registered here, enable and disable work through the Scheduler API while the cron stays fixed -- pause
 * yes, re-tune no. It needs the external scheduler, which {@code CbomSyncTask} already needs.
 *
 * <p>
 * Not {@code @Transactional}: {@code SchedulerListener} already opens one, and the sweeper's own is the second.
 */
@Component
@NoArgsConstructor
public class CryptoAssetPqcSweepTask implements ScheduledJobTask {

    public static final String NAME = "CryptoAssetPqcSweepTask";

    /** Hourly at :30, offset from {@code CbomSyncTask} so the two do not contend. */
    private static final String CRON_EXPRESSION = "0 30 * ? * *";

    private PqcVerdictSweeper sweeper;

    @Autowired
    public void setSweeper(PqcVerdictSweeper sweeper) {
        this.sweeper = sweeper;
    }

    @Override
    public String getDefaultJobName() {
        return NAME;
    }

    @Override
    public String getDefaultCronExpression() {
        return CRON_EXPRESSION;
    }

    @Override
    public boolean isDefaultOneTimeJob() {
        return false;
    }

    @Override
    public String getJobClassName() {
        return this.getClass().getName();
    }

    @Override
    public boolean isSystemJob() {
        return true;
    }

    /**
     * A run that swept nothing is a skip: until ingest gains a caller this fires hourly and finds nothing, and a
     * SUCCESS row an hour would bury the runs that did something. A row the rule set threw on was swept -- it now
     * carries an UNKNOWN verdict at the current generation and no later sweep will revisit it -- so a run with any such
     * row is a failure the operator has to see, as is an aborted sweep, whatever the earlier batches managed to write.
     */
    @Override
    public ScheduledTaskResult performJob(final ScheduledJobInfo scheduledJobInfo, final Object taskData) {
        PqcVerdictSweeper.SweepOutcome outcome = sweeper.sweep();
        int swept = outcome.evaluated() + outcome.failed();
        if (!outcome.ran() || (swept == 0 && !outcome.aborted())) {
            throw new ScheduledJobSkippedException();
        }
        String message = "Swept %d cryptographic asset(s) in %d batch(es); %d verdict(s) written, %d could not be evaluated and were recorded as UNKNOWN"
                .formatted(swept, outcome.batches(), outcome.written(), outcome.failed());
        if (outcome.aborted()) {
            return new ScheduledTaskResult(SchedulerJobExecutionStatus.FAILED,
                    "The sweep aborted before completing. " + message, Resource.CRYPTO_ASSET, null);
        }
        if (outcome.failed() > 0) {
            return new ScheduledTaskResult(SchedulerJobExecutionStatus.FAILED, message, Resource.CRYPTO_ASSET, null);
        }
        return new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, message, Resource.CRYPTO_ASSET, null);
    }
}

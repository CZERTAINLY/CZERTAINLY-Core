package com.otilm.core.events.handlers;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.events.data.DiscoveryResult;
import com.otilm.core.events.handlers.discovery.DiscoveryRunCounts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateDiscoveredEventHandlerStatusTest {

    private static final String ORIGINAL = "Downloaded 100 % of discovered certificates from provider (7 / 7)";
    private static final String TRAILER = "See the discovery certificate list for per-certificate detail.";

    @Test
    void cleanRunReportsProcessingAndKeepsTheOriginalMessage() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(
                new DiscoveryRunCounts(0, 0, 0, 0), ORIGINAL);

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.PROCESSING);
        assertThat(result.getMessage()).isEqualTo(ORIGINAL);
    }

    @Test
    void inventoryGapsAloneAreReported() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(
                new DiscoveryRunCounts(3, 0, 0, 0), ORIGINAL);

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.WARNING);
        assertThat(result.getMessage())
                .isEqualTo("3 certificate(s) could not be imported into the inventory. " + TRAILER);
    }

    @Test
    void keyGapsAloneAreReported() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(
                new DiscoveryRunCounts(0, 2, 0, 0), ORIGINAL);

        assertThat(result.getMessage())
                .isEqualTo("2 certificate(s) were imported without a public key association. " + TRAILER);
    }

    /**
     * Replaces an earlier test that asserted the errored-certificate count took precedence over the key
     * association. That precedence was the defect: a run with both failures reported only one of them.
     */
    @Test
    void everyConditionContributesAndNoneMasksAnother() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(
                new DiscoveryRunCounts(3, 2, 1, 4), ORIGINAL);

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.WARNING);
        assertThat(result.getMessage()).isEqualTo(
                "3 certificate(s) could not be imported into the inventory. "
                        + "2 certificate(s) were imported without a public key association. "
                        + "1 certificate(s) could not be processed to a result. "
                        + "Some per-certificate detail could not be recorded. " + TRAILER);
    }

    @Test
    void aBookkeepingFailureAloneStillWarns() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(
                new DiscoveryRunCounts(0, 0, 0, 1), ORIGINAL);

        assertThat(result.getDiscoveryStatus())
                .as("the persisted detail is knowingly incomplete, so the run is not clean")
                .isEqualTo(DiscoveryStatus.WARNING);
        assertThat(result.getMessage())
                .isEqualTo("Some per-certificate detail could not be recorded. " + TRAILER);
    }

    @Test
    void notAttemptedAloneStillWarns() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(
                new DiscoveryRunCounts(0, 0, 5, 0), ORIGINAL);

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.WARNING);
        assertThat(result.getMessage())
                .isEqualTo("5 certificate(s) could not be processed to a result. " + TRAILER);
    }
}

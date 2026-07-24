package com.otilm.core.events.handlers;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import com.otilm.core.events.data.DiscoveryResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateDiscoveredEventHandlerStatusTest {

    @Test
    void erroredCertificates_reportsWarningWithCount() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(3, false, "orig");

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.WARNING);
        assertThat(result.getMessage()).isEqualTo("3 certificate(s) could not be processed during discovery.");
    }

    @Test
    void keyAssociationIncompleteWithoutErroredCertificates_reportsWarning() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(0, true, "orig");

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.WARNING);
        assertThat(result.getMessage()).isEqualTo("Some discovered certificate keys could not be associated during discovery.");
    }

    @Test
    void erroredCertificatesTakePrecedenceOverKeyAssociation() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(2, true, "orig");

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.WARNING);
        assertThat(result.getMessage()).isEqualTo("2 certificate(s) could not be processed during discovery.");
    }

    @Test
    void cleanRun_reportsProcessingWithOriginalMessage() {
        DiscoveryResult result = CertificateDiscoveredEventHandler.decideFinalStatus(0, false, "orig");

        assertThat(result.getDiscoveryStatus()).isEqualTo(DiscoveryStatus.PROCESSING);
        assertThat(result.getMessage()).isEqualTo("orig");
    }
}

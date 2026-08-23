package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.ConnectorEntityNotFoundException;
import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.ConnectorProblemException;
import com.otilm.api.model.common.error.ErrorCode;
import com.otilm.api.model.common.error.ProblemDetailExtended;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two questions every tick asks about a connector failure: is this definitive, and what may an operator be told.
 * Both answers are load-bearing — the first decides whether a run ends now or spends its budget, the second reaches an
 * API response.
 */
class DiscoveryConnectorErrorsTest {

    private static final URI INSTANCE = URI.create("https://example.com/discovery");

    // ------------------------------------------------------------------ is the run still tracked

    @Test
    void notTrackedOn404_isDefinitive() {
        assertThat(DiscoveryConnectorErrors.isRunNoLongerTracked(problem(ErrorCode.OPERATION_NOT_TRACKED))).isTrue();
    }

    @Test
    void terse404WithNoProblemBody_isDefinitiveToo() {
        // What the AMQP proxy leaves after discarding the problem body. Reading only the error code would miss
        // every tunneled 404 and leave the run burning its whole budget against a connector that has forgotten it.
        assertThat(DiscoveryConnectorErrors.isRunNoLongerTracked(new ConnectorEntityNotFoundException("gone")))
                .isTrue();
    }

    @Test
    void notTrackedCodeOnANon404_isNotDefinitive() {
        // REGISTRATION_NOT_FOUND is declared on a 422, where it means something else entirely. The status has to
        // gate the code, or a 422 would be answered as "this run no longer exists".
        assertThat(DiscoveryConnectorErrors.isRunNoLongerTracked(problem(ErrorCode.REGISTRATION_NOT_FOUND))).isFalse();
    }

    @Test
    void unrelatedCodeOn404_isNotDefinitive() {
        assertThat(DiscoveryConnectorErrors.isRunNoLongerTracked(problem(ErrorCode.INTERNAL_SERVER_ERROR))).isFalse();
    }

    @Test
    void plainConnectorFailure_isNotDefinitive() {
        assertThat(DiscoveryConnectorErrors.isRunNoLongerTracked(new ConnectorException("connection refused")))
                .isFalse();
    }

    @Test
    void nonConnectorFailure_isNotDefinitive() {
        assertThat(DiscoveryConnectorErrors.isRunNoLongerTracked(new IllegalStateException("boom"))).isFalse();
    }

    // ------------------------------------------------------------------ what an operator is told

    @Test
    void knownCodes_yieldCoreAuthoredText() {
        assertThat(DiscoveryConnectorErrors.describe(problem(ErrorCode.OPERATION_NOT_TRACKED)))
                .isEqualTo("the connector no longer tracks the run");
        assertThat(DiscoveryConnectorErrors.describe(problem(ErrorCode.UNAUTHORIZED)))
                .isEqualTo("the connector refused Core's credentials");
        assertThat(DiscoveryConnectorErrors.describe(problem(ErrorCode.SERVICE_UNAVAILABLE)))
                .isEqualTo("the connector was unreachable");
        assertThat(DiscoveryConnectorErrors.describe(problem(ErrorCode.CHECKPOINT_LOST)))
                .isEqualTo("the connector lost the run's checkpoint");
    }

    @Test
    void connectorSuppliedProse_neverReachesTheRun() {
        ProblemDetailExtended detail = ProblemDetailExtended
                .fromErrorCode(ErrorCode.INTERNAL_SERVER_ERROR,
                        "NullPointerException at scanner.internal.Host(10.0.0.7:8443) using cert /etc/ssl/priv.pem",
                        INSTANCE, null);

        String described = DiscoveryConnectorErrors.describe(new ConnectorProblemException(detail));

        // The run's message is a user-visible surface. An obligation on the connector to curate its detail is not
        // a guarantee, so nothing it wrote is forwarded — only text keyed on the closed error-code vocabulary.
        assertThat(described).isEqualTo("the connector reported an internal error");
        assertThat(described).doesNotContain("10.0.0.7", "priv.pem", "NullPointerException");
    }

    @Test
    void unmappedCodeAndPlainFailure_fallBackToTheGenericPhrase() {
        assertThat(DiscoveryConnectorErrors.describe(problem(ErrorCode.POLICY_VIOLATION)))
                .isEqualTo("the connector did not answer");
        assertThat(DiscoveryConnectorErrors.describe(new ConnectorException("connection refused")))
                .isEqualTo("the connector did not answer");
    }

    private static ConnectorProblemException problem(ErrorCode code) {
        return new ConnectorProblemException(ProblemDetailExtended.fromErrorCode(code, "detail", INSTANCE, null));
    }
}

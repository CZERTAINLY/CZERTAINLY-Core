package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.ConnectorEntityNotFoundException;
import com.otilm.api.exception.ConnectorProblemException;
import com.otilm.api.model.common.error.ProblemDetailExtended;
import com.otilm.core.service.handler.authority.ConnectorOperationErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * Classification of connector failures shared by the discovery tick workers, so the status and drain paths cannot drift
 * on what counts as a definitive refusal.
 */
public final class DiscoveryConnectorErrors {

    private static final String UNANSWERED = "the connector did not answer";

    private DiscoveryConnectorErrors() {
    }

    /**
     * Text describing a connector failure that is safe to put on the run, where API clients read it.
     *
     * <p>
     * A raw {@code getMessage()} is never forwarded: it carries transport and provider internals — host names, TLS
     * details, driver text — and the published schema for these fields promises curated text and no raw exception
     * messages. The one thing that is forwarded is an RFC 9457 {@code detail}, which the contract already obliges the
     * connector to curate. Everything else is classified. The full exception still reaches the log.
     */
    public static String describe(Throwable e) {
        if (e instanceof ConnectorProblemException problem && problem.getProblemDetail() != null
                && problem.getProblemDetail().getDetail() != null
                && !problem.getProblemDetail().getDetail().isBlank()) {
            return problem.getProblemDetail().getDetail();
        }
        return UNANSWERED;
    }

    /**
     * True when the connector says it no longer tracks the run. The contract makes this definitive — the connector-side
     * run and its checkpoint are gone, so no amount of retrying brings them back and the run ends FAILED.
     *
     * <p>
     * Both shapes a 404 arrives in are accepted, because the two transports carry different amounts of detail. Over
     * REST the connector's RFC 9457 body survives and the error code is readable; over the AMQP proxy the body is
     * discarded and the client raises {@link ConnectorEntityNotFoundException}, a plain {@code ConnectorException} — so
     * testing only for a problem exception would miss every tunneled 404 and leave the run burning its whole attempt
     * budget against a connector that has already forgotten it.
     *
     * <p>
     * <b>The status gates the code, not the other way round.</b> {@code REGISTRATION_NOT_FOUND} — which the shared
     * predicate accepts as authority's flavour of not-tracked — is declared on a 422, and a 422 means something else
     * entirely here. Read as an {@code int} rather than through {@code getHttpStatus()}, which calls
     * {@code HttpStatus.valueOf} and throws for a valid code with no enum constant such as 499, replacing the
     * connector's own failure with an unrelated one. This mirrors {@code DiscoveryApiClient.isRunNotTracked}, the
     * library's own predicate for the same question.
     */
    public static boolean isRunNoLongerTracked(Throwable e) {
        if (e instanceof ConnectorProblemException problem) {
            ProblemDetailExtended detail = problem.getProblemDetail();
            return detail != null && detail.getStatus() == HttpStatus.NOT_FOUND.value()
                    && ConnectorOperationErrorCodes.isOperationNotTracked(detail.getErrorCode());
        }
        return e instanceof ConnectorEntityNotFoundException;
    }
}

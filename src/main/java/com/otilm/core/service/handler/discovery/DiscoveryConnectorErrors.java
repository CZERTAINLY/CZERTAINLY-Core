package com.otilm.core.service.handler.discovery;

import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.ConnectorProblemException;
import com.otilm.api.model.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Classification of connector failures shared by the discovery tick workers, so the status and drain paths cannot drift
 * on what counts as a definitive refusal.
 */
public final class DiscoveryConnectorErrors {

    private DiscoveryConnectorErrors() {
    }

    /**
     * True when the connector says it no longer tracks the run. The contract makes this definitive — the connector-side
     * run and its checkpoint are gone, so no amount of retrying brings them back and the run ends FAILED.
     *
     * <p>
     * Both signals are checked because the two transports carry different amounts of detail: over REST the connector's
     * RFC 9457 body survives as an {@link ErrorCode}, while the AMQP proxy classifies by HTTP status alone and discards
     * the body. Reading only the error code would miss every tunneled 404.
     */
    /**
     * Text describing a connector failure that is safe to put on the run, where API clients read it.
     *
     * <p>
     * A raw {@code getMessage()} is never forwarded: it carries transport and provider internals — host names, TLS
     * details, driver text — and the published schema for these fields promises curated text and no raw exception
     * messages. The one thing that is forwarded is an RFC 9457 {@code detail}, which the contract already obliges the
     * connector to curate. Everything else is classified. The full exception still reaches the log.
     */
    public static String describe(ConnectorException e) {
        if (e instanceof ConnectorProblemException problem && problem.getProblemDetail() != null
                && problem.getProblemDetail().getDetail() != null
                && !problem.getProblemDetail().getDetail().isBlank()) {
            return problem.getProblemDetail().getDetail();
        }
        return "the connector did not answer";
    }

    public static boolean isRunNoLongerTracked(ConnectorException e) {
        if (!(e instanceof ConnectorProblemException problem) || problem.getProblemDetail() == null) {
            return false;
        }
        ErrorCode code = problem.getProblemDetail().getErrorCode();
        return code == ErrorCode.OPERATION_NOT_TRACKED || problem.getHttpStatus() == HttpStatus.NOT_FOUND;
    }
}

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
    public static boolean isRunNoLongerTracked(ConnectorException e) {
        if (!(e instanceof ConnectorProblemException problem) || problem.getProblemDetail() == null) {
            return false;
        }
        ErrorCode code = problem.getProblemDetail().getErrorCode();
        return code == ErrorCode.OPERATION_NOT_TRACKED || problem.getHttpStatus() == HttpStatus.NOT_FOUND;
    }
}

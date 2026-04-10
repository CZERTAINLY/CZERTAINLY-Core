package com.czertainly.core.service.tsa.validator;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;

public class TspRequestValidationException extends TspException {

    public TspRequestValidationException(TspFailureInfo failureInfo, String clientMessage, String logMessage) {
        super(failureInfo, clientMessage, logMessage);
    }
}

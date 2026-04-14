package com.czertainly.core.api.tsp.parser;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;

public class TspRequestParsingException extends TspException {

    TspRequestParsingException(TspFailureInfo failureInfo, String logMessage, String clientMessage) {
        super(failureInfo, logMessage, clientMessage);
    }

}

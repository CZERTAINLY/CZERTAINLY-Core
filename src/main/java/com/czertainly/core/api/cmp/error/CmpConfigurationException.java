package com.czertainly.core.api.cmp.error;

/**
 * This exception is created if a configuration (scope: cmp request/response)
 * is failed or in corrupted state.
 */
public class CmpConfigurationException extends CmpBaseException {

    public CmpConfigurationException(int failureInfo, String errorDetails) {
        super(failureInfo, errorDetails, null);
    }

}

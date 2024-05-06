package com.czertainly.core.api.cmp.error;

import org.bouncycastle.asn1.ASN1OctetString;

/**
 * This exception is created if a configuration (scope: cmp request/response)
 * is failed or in corrupted state.
 */
public class CmpConfigurationException extends CmpBaseException {

    public CmpConfigurationException(int failureInfo, String errorDetails) {
        super(null, failureInfo, errorDetails, null);
    }

    public CmpConfigurationException(ASN1OctetString tid, int failureInfo, String errorDetails) {
        super(tid, failureInfo, errorDetails, null);
    }

}

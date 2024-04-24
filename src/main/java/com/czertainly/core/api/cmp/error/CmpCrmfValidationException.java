package com.czertainly.core.api.cmp.error;

import com.czertainly.core.api.cmp.message.builder.PkiMessageError;
import org.bouncycastle.asn1.cmp.PKIBody;

public class CmpCrmfValidationException extends CmpException {

    private final int typeOfEnrollment;

    /**
     * @param typeOfEnrollment PKI message type of enrollment request
     * @param failureInfo       CMP failInfo proposed for CMP error message
     * @param errorDetails   description of some details related to the error
     */
    public CmpCrmfValidationException(
            int typeOfEnrollment, int failureInfo, String errorDetails) {
        super(failureInfo, errorDetails);
        this.typeOfEnrollment = typeOfEnrollment;
    }

    public PKIBody toPKIBody() {
        return PkiMessageError.generateCrmfErrorBody(typeOfEnrollment + 1,
                failureInfo, errorDetails);
    }
}

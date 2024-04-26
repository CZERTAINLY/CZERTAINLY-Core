package com.czertainly.core.api.cmp.error;

import org.bouncycastle.asn1.cmp.PKIBody;

public class CmpCrmfValidationException extends CmpProcessingException {

    private final int typeOfRequestEnrollment;

    /**
     * @param typeOfRequestEnrollment PKI message type of enrollment request
     * @param failureInfo    information about error message (CMP level)
     * @param errorDetails   description of some details related to the error
     *
     * @see {@link PKIBody#getType()} to see which type are possible
     */
    public CmpCrmfValidationException(
            int typeOfRequestEnrollment, int failureInfo, String errorDetails) {
        super(failureInfo, errorDetails);
        this.typeOfRequestEnrollment = typeOfRequestEnrollment;
    }

    @Override
    public PKIBody toPKIBody() {
        int typeOfResponseEnrollment = this.typeOfRequestEnrollment+1;
        return PkiMessageError.generateCrmfErrorBody(typeOfResponseEnrollment,
                failureInfo, errorDetails);
    }
}

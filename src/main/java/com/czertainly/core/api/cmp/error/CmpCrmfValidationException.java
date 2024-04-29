package com.czertainly.core.api.cmp.error;

import com.czertainly.core.service.cmp.message.PkiMessageError;
import org.bouncycastle.asn1.cmp.PKIBody;

/**
 * This exception is created if a enrollment (scope: cmp request, types: ir, cr, kur)
 * is failed or in corrupted state.
 */
public class CmpCrmfValidationException extends CmpProcessingException {

    private final int typeOfRequestEnrollment;

    /**
     * @param typeOfRequestEnrollment PKI message type of enrollment request, {@link PKIBody#getType()}
     * @param failureInfo    information about error message (CMP level)
     * @param errorDetails   description of some details related to the error
     */
    public CmpCrmfValidationException(
            int typeOfRequestEnrollment, int failureInfo, String errorDetails) {
        super(failureInfo, errorDetails);
        this.typeOfRequestEnrollment = typeOfRequestEnrollment;
    }

    /**
     * Prepare response type from request type (using: requestType+1)
     * @return create {@link PKIBody} for response message
     */
    @Override
    public PKIBody toPKIBody() {
        return PkiMessageError.generateCrmfErrorBody(this.typeOfRequestEnrollment+1,
                failureInfo, errorDetails);
    }
}

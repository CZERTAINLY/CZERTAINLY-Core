package com.czertainly.core.api.cmp;

import com.czertainly.core.api.cmp.message.PkiMessageError;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;

/**
 * Exception for error handling for cmp failure scenarios.
 *
 * citation: This message MAY be generated at any time during a PKI transaction. --> if exception is raised
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.21">...</a>
 */
public class CmpRuntimeException extends Exception {

    private int failureInfo;
    private ImplFailureInfo implFailureInfo;

    /**
     * @param failureInfo protocol-based error by rfc4120 (type as ${@link PKIFailureInfo})
     * @param implFailureInfo implementation-based error (czertainly scope, see ${@link ImplFailureInfo})
     */
    public CmpRuntimeException(PKIFailureInfo failureInfo, ImplFailureInfo implFailureInfo) {
        this(failureInfo.intValue(),implFailureInfo);
    }

    /**
     * @param failureInfo protocol-based error by rfc4120 (type as integer, see ${@link PKIFailureInfo})
     * @param implFailureInfo implementation-based error (czertainly scope, see ${@link ImplFailureInfo})
     */
    public CmpRuntimeException(int failureInfo, ImplFailureInfo implFailureInfo) {
        this.failureInfo = failureInfo;
        this.implFailureInfo = implFailureInfo;
    }

    /**
     * @return help to build {@link PKIBody} for response flow
     */
    public PKIBody toPKIBody() {
        return PkiMessageError.generateBody(failureInfo, implFailureInfo);
    }
}

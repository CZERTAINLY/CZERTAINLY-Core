package com.czertainly.core.api.cmp.error;

import com.czertainly.core.api.cmp.CmpController;
import com.czertainly.core.api.cmp.message.PkiMessageError;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exception for error handling for cmp failure scenarios.
 *
 * citation (from rfc4210):
 *      This message MAY be generated at any time during a PKI transaction.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.21">...</a>
 */
public class CmpException extends BaseException {

    private static final Logger LOG = LoggerFactory.getLogger(CmpController.class.getName());

    private int failureInfo;
    private ImplFailureInfo implFailureInfo;

    /**
     * @param failureInfo protocol-based error by rfc4120 (type as ${@link PKIFailureInfo})
     * @param implFailureInfo implementation-based error (czertainly scope, see ${@link ImplFailureInfo})
     */
    public CmpException(int failureInfo, ImplFailureInfo implFailureInfo) {
        this(failureInfo,implFailureInfo, null);
    }

    /**
     * @param failureInfo protocol-based error by rfc4120 (type as ${@link PKIFailureInfo})
     * @param implFailureInfo implementation-based error (czertainly scope, see ${@link ImplFailureInfo})
     * @param ex failure reason
     */
    public CmpException(PKIFailureInfo failureInfo, ImplFailureInfo implFailureInfo, Exception ex) {
        this(failureInfo.intValue(),implFailureInfo, ex);
    }

    /**
     * @param failureInfo protocol-based error by rfc4120 (type as integer, see ${@link PKIFailureInfo})
     * @param implFailureInfo implementation-based error (czertainly scope, see ${@link ImplFailureInfo})
     * @param ex failure reason
     */
    public CmpException(int failureInfo, ImplFailureInfo implFailureInfo, Exception ex) {
        super(failureInfo, implFailureInfo.name() + "("+implFailureInfo.getCode()+"): "+ implFailureInfo.getDescription(), ex);
        this.failureInfo = failureInfo;
        this.implFailureInfo = implFailureInfo;
    }

    /**
     * @param failureInfo protocol-based error by rfc4120 (type as integer, see ${@link PKIFailureInfo})
     * @param errorDetails string description of error
     */
    public CmpException(int failureInfo, String errorDetails) {
        super(failureInfo,errorDetails, null);
    }

    /**
     * @param failureInfo protocol-based error by rfc4120 (type as integer, see ${@link PKIFailureInfo})
     * @param errorDetails string description of error
     * @param ex failure reason
     */
    public CmpException(int failureInfo, String errorDetails, Exception ex) {
        super(failureInfo,errorDetails, ex);
    }

    /**
     * @return help to build {@link PKIBody} for response flow
     */
    public PKIBody toPKIBody() {
        return PkiMessageError.generateBody(failureInfo, implFailureInfo);
    }

    @Override
    public String toString() { return "CmpException [failInfo=" + failureInfo + ", implFailureInfo=" + implFailureInfo + "]"; }
}

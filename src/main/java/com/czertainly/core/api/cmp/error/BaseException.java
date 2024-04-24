package com.czertainly.core.api.cmp.error;

import com.czertainly.core.api.cmp.message.builder.PkiMessageError;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseException extends Exception {

    private static final Logger LOG = LoggerFactory.getLogger(BaseException.class.getName());

    protected final int failureInfo;
    protected final String errorDetails;

    protected BaseException(Exception ex) {
        super(ex);

        if (ex instanceof BaseException) {
            this.failureInfo = ((BaseException) ex).failureInfo;
            this.errorDetails = ((BaseException) ex).errorDetails;
        } else {
            this.failureInfo = PKIFailureInfo.systemFailure;
            this.errorDetails = ex.getLocalizedMessage();
            LOG.error("exception at: ", ex);
        }
    }

    /**
     * @param failureInfo   cmp failureInfo for CMP error message
     * @param errorDetails  description of details related to the error
     * @param ex            the underlying exception
     */
    protected BaseException(int failureInfo, String errorDetails, Throwable ex) {
        super(ex == null || ex.getMessage() == null ? errorDetails : ex.getMessage(),
                ex == null || ex.getCause() == null ? ex : ex.getCause());

        if (ex instanceof BaseException) {
            this.failureInfo = ((BaseException) ex).failureInfo;
            this.errorDetails = ((BaseException) ex).errorDetails;
            LOG.error("exception at: {}", errorDetails, ex);
        } else {
            this.failureInfo = failureInfo;
            this.errorDetails = errorDetails;
            if (ex != null) {
                LOG.error("exception at: ", ex);
            } else {
                LOG.error("error at: {}", errorDetails);
            }
        }
    }

    /**
     * @return help to build {@link PKIBody} for response flow
     */
    public PKIBody toPKIBody() { return PkiMessageError.generateBody(failureInfo, errorDetails); }

    @Override
    public String toString() { return "CmpException [failureInfo=" + failureInfo + ", errorDetails=" + errorDetails + "]"; }

}

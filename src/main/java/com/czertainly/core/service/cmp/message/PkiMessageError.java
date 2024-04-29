package com.czertainly.core.service.cmp.message;

import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.cmp.*;

import java.io.IOException;

/**
 *    Error Message Content
 *    <p>This data structure MAY be used by EE, CA, or RA to convey error
 *    info.</p>
 *    <pre>
 *    ErrorMsgContent ::= SEQUENCE {
 *         pKIStatusInfo          PKIStatusInfo,
 *         errorCode              INTEGER           OPTIONAL,
 *         errorDetails           PKIFreeText       OPTIONAL
 *    }
 *    </pre>
 *
 *    <p>
 *    This message MAY be generated at any time during a PKI transaction.
 *    If the client sends this request, the server MUST respond with a
 *    PKIConfirm response, or another ErrorMsg if any part of the header is
 *    not valid.  Both sides MUST treat this message as the end of the
 *    transaction (if a transaction is in progress).
 *    </p>
 *    <p>
 *    If protection is desired on the message, the client MUST protect it
 *    using the same technique (i.e., signature or MAC) as the starting
 *    message of the transaction.  The CA MUST always sign it with a
 *    signature key.
 *    </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.21">...</a>
 */
public class PkiMessageError {

    public static PKIHeader generateHeader() {
        return new PKIHeader(PKIHeader.CMP_2000,
                PKIHeader.NULL_NAME,
                PKIHeader.NULL_NAME);
    }

    public static PKIMessage unprotectedMessage(PKIHeader header, PKIBody body) {
        return new PKIMessage(header, body);
    }

    public static PKIMessage unprotectedMessage(PKIHeader header, Exception ex) {
        return new PKIMessage(header,
                generateBody(PKIFailureInfo.systemFailure, ex.getMessage()));
    }

    public static PKIMessage unprotectedMessage(int failInfo, ImplFailureInfo implFailureInfo) {
        return unprotectedMessage(generateHeader(), failInfo, implFailureInfo);
    }

    public static PKIMessage unprotectedMessage(PKIHeader header, int failInfo, ImplFailureInfo implFailureInfo) {
        if (implFailureInfo == null) throw new IllegalStateException("error handling: there must be known purpose of implementation error");
        return new PKIMessage(header,
                generateBody(failInfo, implFailureInfo));
    }

    public static PKIBody generateBody(int failInfo, ImplFailureInfo implFailureInfo) {
        PKIStatusInfo pkiStatusInfo = pkiStatusInfo(failInfo, implFailureInfo);
        ErrorMsgContent errorMsgContent = errorMsgContent(pkiStatusInfo, implFailureInfo);
        return new PKIBody(PKIBody.TYPE_ERROR, errorMsgContent);
    }

    public static PKIBody generateBody(int failInfo, String errorDetails) {
        PKIStatusInfo pkiStatusInfo = pkiStatusInfo(failInfo, errorDetails);
        ErrorMsgContent errorMsgContent = errorMsgContent(pkiStatusInfo, -1, errorDetails);
        return new PKIBody(PKIBody.TYPE_ERROR, errorMsgContent);
    }

    // crmf - ip, cp, kup body
    public static PKIBody generateCrmfErrorBody(final int bodyType, final int failInfo, final String errorDetails) {
        final PKIStatusInfo pkiStatusInfo =
                new PKIStatusInfo(PKIStatus.rejection, new PKIFreeText(errorDetails), new PKIFailureInfo(failInfo));
        final CertResponse[] response = {new CertResponse(new ASN1Integer(0), pkiStatusInfo)};
        return new PKIBody(bodyType, new CertRepMessage(null, response));
    }

    /**
     * ANS.1 structure of PKIStatusInfo
     * <pre>
     *  PKIStatusInfo ::= SEQUENCE {
     *      status        PKIStatus,
     *      statusString  PKIFreeText     OPTIONAL,
     *      -- implementation-specific error codes
     *      failInfo      PKIFailureInfo  OPTIONAL
     *      -- implementation-specific error details
     *  }
     * </pre>
     *
     * @param failInfo cmp protocol based error state
     * @param errorFailure implementation-specific error state
     *
     * @return create PKIStatusInfo element for given error state
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.2.3">...</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-F">...</a>
     */
    private static PKIStatusInfo pkiStatusInfo(int failInfo, ImplFailureInfo errorFailure){
        return pkiStatusInfo(failInfo, errorFailure.name() + ": " +errorFailure.getDescription());
    }

    private static PKIStatusInfo pkiStatusInfo(int failInfo, String errorDetails){
        PKIFreeText statusText;
        if(errorDetails == null || errorDetails.isBlank()) {
            statusText=new PKIFreeText("");
        } else {
            statusText=new PKIFreeText(errorDetails);
        }
        return new PKIStatusInfo(
                PKIStatus.rejection,//TODO [toce] when error => always 'rejection' (check spec)
                statusText, new PKIFailureInfo(failInfo));
    }

    /**
     * ANS1.structure of ErrorMsgContent
     * <pre>
     *  ErrorMsgContent ::= SEQUENCE {
     *      pKIStatusInfo          PKIStatusInfo,
     *      errorCode              INTEGER           OPTIONAL,
     *      errorDetails           PKIFreeText       OPTIONAL
     *  }
     * </pre>
     *
     * @param pkiStatusInfo protocol error state
     * @param implFailureInfo implementation error state
     *
     * @return create ErrorMsgContent element
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.21">...</a>
     */
    private static ErrorMsgContent errorMsgContent(PKIStatusInfo pkiStatusInfo,
                                                   ImplFailureInfo implFailureInfo) {
        return errorMsgContent(pkiStatusInfo,
                implFailureInfo.getCode(), implFailureInfo.getDescription());
    }
    private static ErrorMsgContent errorMsgContent(PKIStatusInfo pkiStatusInfo,
                                                   int code, String errorText) {
        return new ErrorMsgContent(
                pkiStatusInfo,
                new ASN1Integer(code),
                new PKIFreeText(new DERUTF8String(errorText)));
    }

    /**
     * @throws IllegalStateException if given <code>pkiResponse</code> could not be DER encoded
     */
    public static byte[] encode(PKIMessage pkiResponse) {
        try{
            return pkiResponse.getEncoded();
        } catch (IOException e) {
            throw new IllegalStateException("PKIMessage could not be encoded");
        }
    }
}

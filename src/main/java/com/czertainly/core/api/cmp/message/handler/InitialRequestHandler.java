package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.core.api.cmp.CmpRuntimeException;
import com.czertainly.core.api.cmp.ImplFailureInfo;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;

/**
 * Interface how to handle incoming Initial Request (ir) message from client.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-4.2.1.1">...</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.1">...</a>
 */
public class InitialRequestHandler implements MessageHandler {

    /**
     *
     * @param request
     * @return
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.1">...</a>
     */
    @Override
    public PKIMessage handle(PKIMessage request) throws CmpRuntimeException {
        throw new CmpRuntimeException(PKIFailureInfo.badDataFormat, ImplFailureInfo.TODO);
    }

}

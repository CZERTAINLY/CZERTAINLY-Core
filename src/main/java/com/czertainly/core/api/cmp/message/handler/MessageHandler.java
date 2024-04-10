package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.core.api.cmp.CmpRuntimeException;
import org.bouncycastle.asn1.cmp.PKIMessage;

public interface MessageHandler {

    PKIMessage handle(PKIMessage request) throws CmpRuntimeException;

}

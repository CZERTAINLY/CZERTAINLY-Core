package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.core.api.cmp.error.CmpException;
import org.bouncycastle.asn1.cmp.PKIMessage;

public interface MessageHandler {

    PKIMessage handle(PKIMessage request) throws CmpException;

}

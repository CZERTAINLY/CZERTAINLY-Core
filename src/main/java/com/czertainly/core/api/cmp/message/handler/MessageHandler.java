package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import org.bouncycastle.asn1.cmp.PKIMessage;

public interface MessageHandler {

    PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpException;

}

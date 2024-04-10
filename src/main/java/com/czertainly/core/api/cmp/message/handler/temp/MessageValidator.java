package com.czertainly.core.api.cmp.message.handler.temp;

import com.czertainly.core.api.cmp.message.handler.MessageHandler;
import org.bouncycastle.asn1.cmp.PKIBody;

import java.util.Set;

public interface MessageValidator extends MessageHandler {

    public static final Set<Integer> SUPPORTED_MESSAGES_TYPES = Set.of(
            PKIBody.TYPE_INIT_REQ,
            PKIBody.TYPE_CERT_REQ,
            PKIBody.TYPE_KEY_UPDATE_REQ,
            PKIBody.TYPE_CERT_CONFIRM,
            PKIBody.TYPE_CONFIRM,
            PKIBody.TYPE_REVOCATION_REQ);
}

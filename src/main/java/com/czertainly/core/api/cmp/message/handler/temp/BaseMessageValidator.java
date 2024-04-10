package com.czertainly.core.api.cmp.message.handler.temp;

import com.czertainly.core.api.cmp.CmpRuntimeException;
import com.czertainly.core.api.cmp.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.handler.MessageHandler;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class BaseMessageValidator implements MessageValidator {

    private static final Logger LOG = LoggerFactory.getLogger(BaseMessageValidator.class.getName());

    private final MessageHandler processor;

    public BaseMessageValidator(MessageProcessor processor) {
        this.processor = processor;
    }

    @Override
    public PKIMessage handle(PKIMessage request) throws CmpRuntimeException {
        if(!SUPPORTED_MESSAGES_TYPES.contains(request.getBody().getType())) {
            LOG.error("cmp TID={} | type of incoming request message is not supported, type={}",
                    request.getHeader().getTransactionID(), request.getBody().getType());
            throw new CmpRuntimeException(PKIFailureInfo.badMessageCheck,
                    ImplFailureInfo.TODO);
        }

        // -- handling response
        PKIMessage response = processor.handle(request);

        // -- RESPONSE VALIDATION (outgoing part)
        if(response.getBody() == null || response.getHeader() == null) {
            LOG.error("cmp TID={} | outgoing response message has invalid format (body/header missing), type={}",
                    request.getHeader().getTransactionID(), request.getBody().getType());
            throw new CmpRuntimeException(PKIFailureInfo.badMessageCheck,
                    ImplFailureInfo.TODO);
        }
        return response;
    }
}

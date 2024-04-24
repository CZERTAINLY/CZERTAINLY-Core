package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;
import org.bouncycastle.asn1.cmp.PKIMessage;

/**
 * Bidirectional validator for {@link PKIMessage} object
 *
 * @param <E> result of incoming validation
 * @param <R> result of outgoing validation
 */
public interface BiValidator<E,R> {

    /**
     * validate incoming request message (from client - CZERTAINLY is server)
     *
     * @param request message incoming from client
     * @return null if validation is ok
     * @throws CmpException if validation has failed
     */
    R validateIn(PKIMessage request) throws CmpException;

    /**
     * validate outgoing response message (from CR/RA - CZERTAINLY is client)
     *
     * @param response message incoming from CA/RA
     * @return null if validation is ok
     * @throws CmpException if validation has failed
     */
    E validateOut(PKIMessage response) throws CmpException;

}

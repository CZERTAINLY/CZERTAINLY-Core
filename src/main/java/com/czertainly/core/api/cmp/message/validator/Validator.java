package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import org.bouncycastle.asn1.cmp.PKIBody;

import java.util.List;

/**
 * Common interface for (single-way) validation
 *
 * @param <I> subject of validation
 * @param <E> return type of validation
 */
public interface Validator<I,E> {

    /**
     * validate given <code>subject</code>
     * @param subject for validation
     * @return result of validation
     * @throws CmpException if validation has failed
     */
    E validate(I subject) throws CmpException;

    /**
     * <pre>
     *     PKIBody ::= CHOICE {
     *          ir       [0]  CertReqMessages,       --Initialization Req
     *          cr       [2]  CertReqMessages,       --Certification Req
     *          kur      [7]  CertReqMessages,       --Key Update Request
     *          krr      [9]  CertReqMessages,       --Key Recovery Req
     *          ccr      [13] CertReqMessages,       --Cross-Cert.  Request
     * </pre>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.2">PKI Body overall</a>
     */
    List<Integer> CRMF_MESSAGES_TYPES = List.of(
            PKIBody.TYPE_INIT_REQ,
            PKIBody.TYPE_CERT_REQ,
            PKIBody.TYPE_KEY_UPDATE_REQ,
            PKIBody.TYPE_KEY_RECOVERY_REQ, // -- not implemented
            PKIBody.TYPE_CROSS_CERT_REQ);  // -- not implemented

    List<Integer> SUPPORTED_CRMF_MESSAGES_TYPES = CRMF_MESSAGES_TYPES.subList(0,3);

}

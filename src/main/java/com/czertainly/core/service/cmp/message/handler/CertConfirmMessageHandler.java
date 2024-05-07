package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for certification confirmation message.
 * <pre>
 *   type:
 *      {@link PKIBody#TYPE_CERT_CONFIRM}
 *   content:
 *      certConf [24] CertConfirmContent,    --Certificate confirm
 * </pre>
 */
@Component
@Transactional
public class CertConfirmMessageHandler implements MessageHandler<PKIMessage> {

    /**
     * <pre>
     *    Certificate confirm; certConf
     *
     *    Field                Value
     *    sender               present
     *      -- same as in ir
     *
     *    recipient            CA name
     *      -- the name of the CA who was asked to produce a certificate
     *
     *    transactionID        present
     *      -- value from corresponding ir and ip messages
     *
     *    senderNonce          present
     *      -- 128 (pseudo-) random bits
     *
     *    recipNonce           present
     *      -- value from senderNonce in corresponding ip message
     *
     *    protectionAlg        MSG_MAC_ALG
     *      -- only MAC protection is allowed for this message.  The
     *      -- MAC is based on the initial authentication key shared
     *      -- between the EE and the CA.
     *
     *    senderKID            referenceNum
     *      -- the reference number which the CA has previously issued
     *      -- to the end entity (together with the MACing key)
     *
     *    body                 certConf
     *      -- see Section 5.3.18, "PKI Confirmation Content", for the
     *      -- contents of the certConf fields.
     *      -- Note: two CertStatus structures are required if both an
     *      -- encryption and a signing certificate were sent.
     *
     *    protection           present
     *      -- bits calculated using MSG_MAC_ALG
     * </pre>
     * <pre>
     *     CertConfirmContent ::= SEQUENCE OF CertStatus
     *          CertStatus ::= SEQUENCE {
     *             certHash    OCTET STRING,
     *             certReqId   INTEGER,
     *             statusInfo  PKIStatusInfo OPTIONAL
     *          }
     * </pre>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.18">Certificate Confirmation Content</a>
     */
    @Override
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpBaseException {
        ASN1OctetString tid = request.getHeader().getTransactionID();
        if(PKIBody.TYPE_CERT_CONFIRM!=request.getBody().getType()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "confirmation (certConf) message cannot be handled - unsupported body rawType="+request.getBody().getType()+", type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()) +"; only type=cerfConf is supported");
        }

        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(new PKIBody(PKIBody.TYPE_CONFIRM, new PKIConfirmContent()))
                    .addExtraCerts(null)
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "problem build pkiConfirm response message, type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()), e);
        }
    }
}

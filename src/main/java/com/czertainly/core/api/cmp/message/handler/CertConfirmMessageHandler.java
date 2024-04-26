package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.api.cmp.message.validator.impl.ProtectionValidator;
import com.czertainly.core.api.cmp.mock.MockCaImpl;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;

/**
 * Handler for certification confirmation message.
 * <pre>
 *   type:
 *      {@link PKIBody#TYPE_CERT_CONFIRM}
 *   content:
 *      certConf [24] CertConfirmContent,    --Certificate confirm
 * </pre>
 */
public class CertConfirmMessageHandler implements MessageHandler {

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
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpException {
        if(PKIBody.TYPE_CERT_CONFIRM!=request.getBody().getType()) {
            throw new CmpProcessingException(
                    PKIFailureInfo.systemFailure,
                    "confirmation (certConf) message cannot be handled - unsupported body rawType="+request.getBody().getType()+", type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()) +"; only type=cerfConf is supported");
        }

        // -- PKIProtection, see https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3
        new ProtectionValidator(configuration)
                .validate(request);

        PKIMessage response = MockCaImpl
                .handleCertConfirm(request, configuration);//.getBody().getContent();

        if(response != null) { return response; }
        throw new CmpProcessingException(
                PKIFailureInfo.systemFailure, "general problem while handling PKIMessage, type=certConf");
    }
}

package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.cmp.CmpTransactionRepository;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    private CertificateRepository certificateRepository;
    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) { this.certificateRepository=certificateRepository; }

    private CmpTransactionRepository cmpTransactionRepository;
    @Autowired
    private void setCmpTransactionRepository(CmpTransactionRepository cmpTransactionRepository) { this.cmpTransactionRepository = cmpTransactionRepository; }

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
     *      -- to the end entity (together with the MAC-ing key)
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
        String msgBodyAsString = PkiMessageDumper.msgTypeAsString(request.getBody().getType());
        if(PKIBody.TYPE_CERT_CONFIRM!=request.getBody().getType()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "confirmation (certConf) message cannot be handled - unsupported body rawType="+request.getBody().getType()+", type="+ msgBodyAsString +"; only type=cerfConf is supported");
        }

        Optional<CmpTransaction> trx = cmpTransactionRepository.findByTransactionId(tid.toString());
        if(trx.isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    "given transaction not found");
        }
        CertConfirmContent certConfirm = (CertConfirmContent ) request.getBody().getContent();
        String incomingFingerprint = certConfirm.toCertStatusArray()[0].getCertHash().toString()
                .substring(1);//remove '#' at 0 index (incoming from pki message)
        Optional<Certificate> certificate = certificateRepository.findByFingerprint(incomingFingerprint);
        if(certificate.isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badCertId,
                    "FP="+incomingFingerprint+" | certificate is not found for given certHash(fingerprint)");
        }
        Certificate trxCertificate = trx.get().getCertificate();
        String trxFingerprint = trxCertificate == null ? null : trxCertificate.getFingerprint();
        if(!incomingFingerprint.equals(trxFingerprint)) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badCertId,
                    "FP="+incomingFingerprint+" | transactionID is not related with incoming certHash(fingerprint)");
        }
        CmpTransaction updatedTransaction = trx.get();
        updatedTransaction.setState(CmpTransaction.CmpTransactionState.CERT_CONFIRMED);
        cmpTransactionRepository.save(updatedTransaction);

        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(new PKIBody(PKIBody.TYPE_CONFIRM, new PKIConfirmContent()))
                    .addExtraCerts(null)
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "problem build pkiConfirm response message, type="+ msgBodyAsString, e);
        }
    }
}

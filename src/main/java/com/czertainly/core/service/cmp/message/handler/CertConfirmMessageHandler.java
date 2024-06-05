package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.api.interfaces.core.cmp.error.ImplFailureInfo;
import com.czertainly.api.model.core.cmp.CmpTransactionState;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.message.CmpTransactionService;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(CertConfirmMessageHandler.class.getName());

    private CertificateRepository certificateRepository;
    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) { this.certificateRepository=certificateRepository; }

    private CmpTransactionService cmpTransactionService;
    @Autowired
    public void setCmpTransactionService(CmpTransactionService cmpTransactionService) { this.cmpTransactionService = cmpTransactionService; }

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

        CertConfirmContent certConfirmBody = (CertConfirmContent) request.getBody().getContent();
        CertStatus[] certStatuses = certConfirmBody.toCertStatusArray();
        for (var certStatus : certStatuses) {
            processConfirmation(tid, certStatus);
        }

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

    private void processConfirmation(ASN1OctetString tid, CertStatus certStatus)
            throws CmpProcessingException {
        // -- find certificate from database (by incoming fingerprint)
        String incomingFingerprint = certStatus.getCertHash().toString()
                .substring(1);//remove '#' at 0 index (incoming from pki message)
        // -- find certificate (by incoming fingerprint)
        Optional<Certificate> certificate = certificateRepository.findByFingerprint(incomingFingerprint);
        if(certificate.isEmpty()) {
            LOG.error("TID={}, FP={} | certificate is not found for given certHash(fingerprint)", tid, incomingFingerprint);
            throw new CmpProcessingException(tid, PKIFailureInfo.badCertId,
                    ImplFailureInfo.CMPHANCERTCONF001);
        }
        Optional<CmpTransaction> relatedTransaction = cmpTransactionService.findByTransactionIdAndFingerprint(
                tid.toString(), incomingFingerprint);
        if(relatedTransaction.isEmpty()) {
            LOG.error("TID={}, FP={} | given transactionId and related certificate are not found", tid, incomingFingerprint);
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    ImplFailureInfo.CMPHANCERTCONF002);
        }
        // -- update transaction
        relatedTransaction.get().setState(CmpTransactionState.CERT_CONFIRMED);
        cmpTransactionService.save(relatedTransaction.get());
    }
}

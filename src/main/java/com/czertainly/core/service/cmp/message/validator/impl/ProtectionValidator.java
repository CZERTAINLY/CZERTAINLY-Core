package com.czertainly.core.service.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.validator.Validator;
import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.CMPObjectIdentifiers;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <pre>[1]
 *       PKIMessage ::= SEQUENCE {
 *          header           PKIHeader,
 *          body             PKIBody,
 *          protection   [0] PKIProtection OPTIONAL,
 *          extraCerts   [1] SEQUENCE SIZE (1..MAX) OF CMPCertificate
 *                           OPTIONAL
 *      }
 * </pre>
 * <p>[2] The protectionAlg field specifies the algorithm used to protect the
 *    message.  If no protection bits are supplied (note that PKIProtection
 *    is OPTIONAL) then this field MUST be omitted; if protection bits are
 *    supplied, then this field MUST be supplied.</p>
 *
 * <p>When protection is applied, the following structure is used:
 *    <pre>
 *         PKIProtection ::= BIT STRING
 *    </pre>
 *    The input to the calculation of PKIProtection is the DER encoding of
 *    the following data structure:
 *    <pre>
 *         ProtectedPart ::= SEQUENCE {
 *             header    PKIHeader,
 *             body      PKIBody
 *    }</pre>
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1">Overall PKI Message</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.1">PKI message header</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3">PKI message protection</a>
 */
@Component
@Transactional
public class ProtectionValidator implements Validator<PKIMessage, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(ProtectionValidator.class.getName());
    
    @Override
    public Void validate(PKIMessage message, ConfigurationContext configuration) throws CmpBaseException {
        ASN1BitString protection = message.getProtection();
        ASN1OctetString tid = message.getHeader().getTransactionID();

        if (protection == null) {
            switch (message.getBody().getType()) {
                case PKIBody.TYPE_ERROR:
                case PKIBody.TYPE_CONFIRM:
                case PKIBody.TYPE_REVOCATION_REP:
                    LOG.warn("TID={} | ignore protection for type={}", tid, PkiMessageDumper.msgTypeAsString(message.getBody()));
                    return null;
                default:
                    throw new CmpProcessingException(PKIFailureInfo.notAuthorized,
                            ImplFailureInfo.CMPVALPRO530);
            }
        }

        final AlgorithmIdentifier protectionAlg = message.getHeader().getProtectionAlg();
        if (protectionAlg == null) {
            throw new CmpProcessingException(PKIFailureInfo.notAuthorized,
                    ImplFailureInfo.CMPVALPRO531);
        }
        if (CMPObjectIdentifiers.passwordBasedMac.equals(protectionAlg.getAlgorithm())) {
            new ProtectionMacValidator().validate(message, configuration);
        } else if (PKCSObjectIdentifiers.id_PBMAC1.equals(protectionAlg.getAlgorithm())) {
            new ProtectionPBMac1Validator().validate(message, configuration);
        } else {
            new ProtectionSignatureValidator().validate(message, configuration);
        }
        return null;
    }
}

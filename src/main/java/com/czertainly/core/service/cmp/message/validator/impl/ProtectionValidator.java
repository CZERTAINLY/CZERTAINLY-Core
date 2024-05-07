package com.czertainly.core.service.cmp.message.validator.impl;

import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.service.cmp.message.validator.BiValidator;
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
public class ProtectionValidator implements BiValidator<Void, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(ProtectionValidator.class.getName());

    @Override
    public Void validateIn(PKIMessage request, ConfigurationContext configuration) throws CmpBaseException {
        ASN1BitString protection = request.getProtection();
        ASN1OctetString tid = request.getHeader().getTransactionID();

        /*
         * The protectionAlg field specifies the algorithm used to protect the
         * message.  If no protection bits are supplied (note that PKIProtection
         * is OPTIONAL) then this field MUST be omitted; if protection bits are
         * supplied, then this field MUST be supplied.
         */
        if (protection == null && request.getHeader().getProtectionAlg() == null) {
            return null;
        }

        if (protection == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.notAuthorized,
                ImplFailureInfo.CMPVALPRO530);
        }

        final AlgorithmIdentifier protectionAlg = request.getHeader().getProtectionAlg();
        if (protectionAlg == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.notAuthorized,
                    ImplFailureInfo.CMPVALPRO532);
        }

        runValidation(configuration, request, "request");
        return null;
    }

    @Override
    public Void validateOut(PKIMessage response, ConfigurationContext configuration) throws CmpBaseException {
        ASN1BitString protection = response.getProtection();
        ASN1OctetString tid = response.getHeader().getTransactionID();

         /*
          * The protectionAlg field specifies the algorithm used to protect the
          * message.  If no protection bits are supplied (note that PKIProtection
          * is OPTIONAL) then this field MUST be omitted; if protection bits are
          * supplied, then this field MUST be supplied.
          */
        if (protection == null && response.getHeader().getProtectionAlg() == null) {
            return null;
        }

        if (protection == null) {
            switch (response.getBody().getType()) {
                case PKIBody.TYPE_ERROR:
                case PKIBody.TYPE_CONFIRM:
                case PKIBody.TYPE_REVOCATION_REP:
                    LOG.warn("TID={} | ignore protection for type={}", tid, PkiMessageDumper.msgTypeAsString(response.getBody()));
                    return null;
                default:
                    throw new CmpProcessingException(tid, PKIFailureInfo.notAuthorized,
                            ImplFailureInfo.CMPVALPRO531);
            }
        }

        final AlgorithmIdentifier protectionAlg = response.getHeader().getProtectionAlg();
        if (protectionAlg == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.notAuthorized,
                    ImplFailureInfo.CMPVALPRO533);
        }

        //runValidation(configuration, response, "response");
        //* cross-validace funguje (pouze kdyz klient pouziva -srvcert/u sig.)
        ProtectionStrategy czrtProtectionStrategy = configuration.getProtectionStrategy();
        String protectionAlgId = czrtProtectionStrategy.getProtectionAlg().getAlgorithm().getId();
        if (CMPObjectIdentifiers.passwordBasedMac.equals(protectionAlgId)) {
            new ProtectionMacValidator().validate(response, configuration);
        } else if (PKCSObjectIdentifiers.id_PBMAC1.equals(protectionAlgId)) {
            new ProtectionPBMac1Validator().validate(response, configuration);
        } else {
            new ProtectionSignatureValidator().validate(response, configuration);
        }
        //*/

        return null;
    }
    
    private void runValidation(ConfigurationContext configuration, PKIMessage message, String typeMessage) throws CmpBaseException {
        ASN1OctetString tid = message.getHeader().getTransactionID();
        AlgorithmIdentifier protectionAlg = message.getHeader().getProtectionAlg();
        ProtectionMethod czrtProtectionMethod = configuration.getProtectionMethod();
        if (CMPObjectIdentifiers.passwordBasedMac.equals(protectionAlg.getAlgorithm())) {
            if(ProtectionMethod.SIGNATURE.equals(czrtProtectionMethod)) {
                throw new CmpConfigurationException(tid, PKIFailureInfo.systemFailure,
                        "wrong ("+typeMessage+") configuration: SIGNATURE is not setup");
            }
            new ProtectionMacValidator().validate(message, configuration);
        } else if (PKCSObjectIdentifiers.id_PBMAC1.equals(protectionAlg.getAlgorithm())) {
            if(ProtectionMethod.SIGNATURE.equals(czrtProtectionMethod)) {
                throw new CmpConfigurationException(tid, PKIFailureInfo.systemFailure,
                        "wrong ("+typeMessage+") configuration: SIGNATURE is not setup");
            }
            new ProtectionPBMac1Validator().validate(message, configuration);
        } else {
            if(ProtectionMethod.SHARED_SECRET.equals(czrtProtectionMethod)) {
                throw new CmpConfigurationException(tid, PKIFailureInfo.systemFailure,
                        "wrong ("+typeMessage+") configuration: SHARED_SECRET is not setup");
            }
            new ProtectionSignatureValidator().validate(message, configuration);
        }
    }
}

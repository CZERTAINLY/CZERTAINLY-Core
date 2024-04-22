package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.cmp.CMPObjectIdentifiers;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

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
public class ProtectionValidator implements Validator<PKIMessage, Void> {

    private final ConfigurationContext configuration;

    public ProtectionValidator(ConfigurationContext configuration){
        this.configuration = configuration;
    }
    
    @Override
    public Void validate(PKIMessage message) throws CmpException {
        final ASN1BitString protection = message.getProtection();
        if (protection == null) {
            throw new CmpException(PKIFailureInfo.notAuthorized,
                    ImplFailureInfo.CRYPTOPRO530);
        }
        final AlgorithmIdentifier protectionAlg = message.getHeader().getProtectionAlg();
        if (protectionAlg == null) {
            throw new CmpException(PKIFailureInfo.notAuthorized,
                    ImplFailureInfo.CRYPTOPRO531);
        }
        if (CMPObjectIdentifiers.passwordBasedMac.equals(protectionAlg.getAlgorithm())) {
            //todo tocecz - password based mac validator
        } else if (PKCSObjectIdentifiers.id_PBMAC1.equals(protectionAlg.getAlgorithm())) {
            //todo tocecz - add id_PBMAC1 validator
        } else {
            new ProtectionSignatureValidator().validate(message);
        }
        return null;
    }
}

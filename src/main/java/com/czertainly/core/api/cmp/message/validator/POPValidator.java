package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.util.BouncyCastleUtil;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.crmf.*;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Objects;

/**
 * <h6>Proof-of-Possession (POP)</h6>
 *
 * <pre>
 *     ProofOfPossession ::= CHOICE {
 *          raVerified        [0] NULL,
 *          signature         [1] POPOSigningKey,
 *          keyEncipherment   [2] POPOPrivKey,
 *          keyAgreement      [3] POPOPrivKey }
 *
 *      POPOSigningKey ::= SEQUENCE {
 *          poposkInput         [0] POPOSigningKeyInput OPTIONAL,
 *          algorithmIdentifier     AlgorithmIdentifier,
 *          signature               BIT STRING }
 * </pre>
 *
 * <ul>
 *     <li><a href="https://www.ssl.com/how-to/proving-possession-of-a-private-key/">example using of POP</a> (at www.ssl.com)</li>
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.2.8">Proof-of-Possession Structures at rfc4210</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.2.8.4"> Summary of PoP Options at rfc4210</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.3">Proof of Possesion Profile at rfc4210</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-4">Proof-of-Possession (POP) at rfc4211</a>
 */
public class POPValidator implements Validator<PKIMessage, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(POPValidator.class.getName());

    /**
     * Try to find public key (from ${@link PKIBody}) and get key and verify signature of POP data
     *
     * @param message which has been used for Proof-of-Possession (POP) verification
     * @return Void
     * @throws CmpException if any problem (technically or with implementation)
     */
    @Override
    public Void validate(PKIMessage message) throws CmpException {
        if(PKIBody.TYPE_INIT_REQ!=message.getBody().getType()) {
            // only Initialization Request (ir) is allowed
            throw new IllegalStateException("cannot use POP verification for given message body/type, type="
                    +message.getBody().getType());
        }

        ASN1OctetString tid = message.getHeader().getTransactionID();
        CertReqMsg certReqMsg = ((CertReqMessages) message.getBody().getContent()).toCertReqMsgArray()[0];
        CertRequest certRequest = certReqMsg.getCertReq();

        // -- public key (from certificate), extract the public key from the certificate
        PublicKey publicKey;
        try {
            SubjectPublicKeyInfo subjectPublicKeyInfo = certRequest.getCertTemplate().getPublicKey();
            publicKey = KeyFactory.getInstance(
                            subjectPublicKeyInfo.getAlgorithm().getAlgorithm().toString(),
                    BouncyCastleUtil.getBouncyCastleProvider()
            ).generatePublic(new X509EncodedKeySpec(subjectPublicKeyInfo.getEncoded(ASN1Encoding.DER)));
        } catch (InvalidKeySpecException|NoSuchAlgorithmException|IOException e) {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP001));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP001, e);//badAlg
        }
        
        // -- signature
        ProofOfPossession proofOfPossession = certReqMsg.getPop();
        if(proofOfPossession == null || ProofOfPossession.TYPE_SIGNING_KEY!=proofOfPossession.getType())
        {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP004));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP004);
        }
        POPOSigningKey popoSigningKey = (POPOSigningKey) proofOfPossession.getObject();
        Signature signature = null;
        try {
            signature = Signature.getInstance(
                    popoSigningKey.getAlgorithmIdentifier().getAlgorithm().getId(), BouncyCastleUtil.getBouncyCastleProvider());
            signature.initVerify(publicKey);
            signature.update(certRequest.getEncoded(ASN1Encoding.DER));
        } catch (NoSuchAlgorithmException|InvalidKeyException|SignatureException|IOException e) {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP002));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP002, e);
        }

        // -- verify using the public key
        boolean verified = false;
        try {
            verified = signature.verify(popoSigningKey.getSignature().getBytes());
        } catch (SignatureException e) {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP003));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP003, e);
        }

        if(!verified)
        {
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP005);
        }

        return null;
    }

    private String createLogMessage(ASN1OctetString transactionId, ImplFailureInfo failureInfo) {
        return "cmp TID="+transactionId+", code="+failureInfo.name()+" | " + failureInfo.getDescription();
    }
}

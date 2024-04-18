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
 * Short fields overview:
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
 * <h6>4.1 Signature Key POP</h6>
 *
 *    <p>
 *    POP for a signature key is accomplished by performing a signature
 *    operation on a piece of data containing the identity for which the
 *    certificate is desired.
 *    </p>
 *
 *    <p>There are three cases that need to be looked at when doing a POP for
 *    a signature key:</p>
 *    <ul>
 *        <li>
 *    1.  The certificate subject has not yet established an authenticated
 *        identity with a CA/RA, but has a password and identity string
 *        from the CA/RA.  In this case, the POPOSigningKeyInput structure
 *        would be filled out using the publicKeyMAC choice for authInfo,
 *        and the password and identity would be used to compute the
 *        publicKeyMAC value.  The public key for the certificate being
 *        requested would be placed in both the POPOSigningKeyInput and the
 *        Certificate Template structures.  The signature field is computed
 *        over the DER-encoded POPOSigningKeyInput structure.</li>
 *
 *        <li>
 *    2.  The CA/RA has established an authenticated identity for the
 *        certificate subject, but the requestor is not placing it into the
 *        certificate request.  In this case, the POPOSigningKeyInput
 *        structure would be filled out using the sender choice for
 *        authInfo.  The public key for the certificate being requested
 *        would be placed in both the POPOSigningKeyInput and the
 *        Certificate Template structures.  The signature field is computed
 *        over the DER-encoded POPOSigningKeyInput structure.</li>
 *
 *    3.  <li>The certificate subject places its name in the Certificate
 *        Template structure along with the public key.  In this case the
 *        poposkInput field is omitted from the POPOSigningKey structure.
 *        The signature field is computed over the DER-encoded certificate
 *        template structure.</li>
 *    </ul>
 *    <pre>
 *    POPOSigningKey ::= SEQUENCE {
 *        poposkInput         [0] POPOSigningKeyInput OPTIONAL,
 *        algorithmIdentifier     AlgorithmIdentifier,
 *        signature               BIT STRING }
 *    </pre>
 *    <p>The fields of POPOSigningKey have the following meaning:</p>
 *
 *       <p><i>poposkInput</i> contains the data to be signed, when present.  This
 *       field MUST be present when the certificate template does not
 *       contain both the public key value and a subject name value.<p>
 *
 *       <p><i>algorithmIdentifier</i> identifiers the signature algorithm and an
 *       associated parameters used to produce the POP value.<p>
 *
 *       </p><i>signature</i> contains the POP value produce.  If poposkInput is
 *       present, the signature is computed over the DER-encoded value of
 *       poposkInput.  If poposkInput is absent, the signature is computed
 *       over the DER-encoded value of certReq.<p>
 *
 *    <pre>
 *    POPOSigningKeyInput ::= SEQUENCE {
 *        authInfo            CHOICE {
 *            sender              [0] GeneralName,
 *            -- used only if an authenticated identity has been
 *            -- established for the sender (e.g., a DN from a
 *            -- previously-issued and currently-valid certificate)
 *            publicKeyMAC        PKMACValue },
 *            -- used if no authenticated GeneralName currently exists for
 *            -- the sender; publicKeyMAC contains a password-based MAC
 *            -- on the DER-encoded value of publicKey
 *        publicKey           SubjectPublicKeyInfo }  -- from CertTemplate
 *    </pre>
 *
 *    <p>
 *    The fields of POPOSigningKeyInput have the following meaning:</p>
 *
 *       <p>
 *       <i>sender</i> contains an authenticated identity that has been previously
 *       established for the subject.</p>
 *
 *       <p>
 *       <i>publicKeyMAC</i> contains a computed value that uses a shared secret
 *       between the CA/RA and the certificate requestor.</p>
 *
 *       <p>
 *       <i>publicKey<i> contains a copy of the public key from the certificate
 *       template.  This MUST be exactly the same value as is contained in
 *       the certificate template.</p>
 *
 *    <pre>
 *    PKMACValue ::= SEQUENCE {
 *       algId  AlgorithmIdentifier,
 *       value  BIT STRING }
 *    </pre>
 *
 *    <p>
 *    The fields of PKMACValue have the following meaning:</p>
 *
 *       <p>
 *       algId identifies the algorithm used to compute the MAC value.  All
 *       implementations MUST support id-PasswordBasedMAC.  The details on
 *       this algorithm are presented in section 4.4.</p>
 *
 *       <p>
 *       value contains the computed MAC value.  The MAC value is computed
 *       over the DER-encoded public key of the certificate subject.</p>
 *
 *    <p>The CA/RA identifies the shared secret to be used by looking at 1)
 *    the general name field in the certificate request or 2) either the
 *    regToken (see section 6.1) or authToken (see section 6.2) controls.</p>
 *
 * @see <a href="https://www.ssl.com/how-to/proving-possession-of-a-private-key/">example using of POP at ssl.com</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.2.8">Proof-of-Possession Structures at rfc4210</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.2.8.4"> Summary of PoP Options at rfc4210</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.3">Proof of Possesion Profile at rfc4210</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-4">Proof-of-Possession (POP) at rfc4211</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-4.1">Signature Key POP at rfc4211</a>
 */
public class POPValidator implements Validator<PKIMessage, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(POPValidator.class.getName());

    /**
     * Find public key (from ${@link PKIBody}/${@link CertTemplate}) and verify signature
     * (client used its private key) of ${@link CertRequest} data.
     *
     * @param message which has been used for Proof-of-Possession (POP) verification
     * @return Void/null is ok
     *
     * @throws CmpException if any problem (technically or with implementation)
     */
    @Override
    public Void validate(PKIMessage message) throws CmpException {
        switch(message.getBody().getType()) {
            case PKIBody.TYPE_INIT_REQ:
            case PKIBody.TYPE_CERT_REQ:
            case PKIBody.TYPE_KEY_UPDATE_REQ:
                break;
            default:
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
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP501));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP501, e);//badAlg
        }
        
        // -- signature
        ProofOfPossession proofOfPossession = certReqMsg.getPop();
        if(proofOfPossession == null){
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP504));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP504);
        }
        if(ProofOfPossession.TYPE_SIGNING_KEY!=proofOfPossession.getType())
        {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP505));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP505);
        }
        POPOSigningKey popoSigningKey = (POPOSigningKey) proofOfPossession.getObject();
        if(popoSigningKey.getPoposkInput() != null) {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP505));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP505);
        }

        Signature signature = null;
        try {
            /*
            POPOSigningKey ::= SEQUENCE {
             poposkInput           [0] POPOSigningKeyInput OPTIONAL,
             algorithmIdentifier   AlgorithmIdentifier,
             signature             BIT STRING }
             -- The signature (using "algorithmIdentifier") is on the
             -- DER-encoded value of poposkInput.  NOTE: If the CertReqMsg
             -- certReq CertTemplate contains the subject and publicKey values,
             -- then poposkInput MUST be omitted and the signature MUST be
             -- computed over the DER-encoded value of CertReqMsg certReq.  If
             -- the CertReqMsg certReq CertTemplate does not contain both the
             -- public key and subject values (i.e., if it contains only one
             -- of these, or neither), then poposkInput MUST be present and
             -- MUST be signed.
             @see https://www.rfc-editor.org/rfc/rfc4211#appendix-B

             The certificate subject places its name in the Certificate
             Template structure along with the public key.  In this case the
             poposkInput field is omitted from the POPOSigningKey structure.
             The signature field is computed over the DER-encoded certificate
             template structure.
             @see https://www.rfc-editor.org/rfc/rfc4211#section-4.1 (point 3)
             */
            byte[] subjectOfVerification = certRequest.getEncoded(ASN1Encoding.DER);
            signature = Signature.getInstance(
                    popoSigningKey.getAlgorithmIdentifier().getAlgorithm().getId(), BouncyCastleUtil.getBouncyCastleProvider());
            signature.initVerify(publicKey);
            signature.update(subjectOfVerification);
        } catch (NoSuchAlgorithmException|InvalidKeyException|SignatureException|IOException e) {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP502, e.getMessage()));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP502, e);
        }

        // -- verify PoP signature using incoming public key
        boolean verified = false;
        try { verified = signature.verify(popoSigningKey.getSignature().getBytes()); }
        catch (SignatureException e) {
            LOG.error(createLogMessage(tid, ImplFailureInfo.CRYPTOPOP503, e.getMessage()));
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP503, e);
        }

        if(!verified)
        {
            throw new CmpException(PKIFailureInfo.badPOP, ImplFailureInfo.CRYPTOPOP506);
        }

        return null;
    }
    private String createLogMessage(ASN1OctetString transactionId, ImplFailureInfo failureInfo) {
        return createLogMessage(transactionId, failureInfo, "");
    }

    private String createLogMessage(ASN1OctetString transactionId, ImplFailureInfo failureInfo, String errorDetails) {
        return "cmp TID="+transactionId+", code="+failureInfo.name()+" | " + failureInfo.getDescription() + " "+errorDetails;
    }
}

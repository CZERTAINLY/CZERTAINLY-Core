package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import com.czertainly.core.api.cmp.message.validator.POPValidator;
import com.czertainly.core.api.cmp.message.validator.ProtectionSignatureBasedValidator;
import com.czertainly.core.api.cmp.message.validator.ProtectionValidator;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.crmf.*;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;

/**
 * <p>Interface how to handle incoming Initial Request (ir) message from client.</p>
 *
 * <h5>Initialization request</h5>
 * <p>
 *      An Initialization request message contains as the PKIBody a
 *      <code>CertReqMessage</code>s data structure, which specifies the requested
 *      certificate(s).  Typically, <code>SubjectPublicKeyInfo</code>, <code>KeyId</code>,
 *      and <code>Validity</code> are the template fields which may be supplied for
 *      each certificate requested (see Appendix D profiles for further information).
 *      This message is intended to be used for entities when first initializing
 *      into the PKI.<br/>
 *      <b>source</b>:https://www.rfc-editor.org/rfc/rfc4210#section-5.3.1
 * </p>
 * <p>See Appendix C and [CRMF] for CertReqMessages syntax. </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-4.2.1.1">[1] - Initial Request</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.1">[2] - CertReqMessages syntax</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-3">[3] - CertRequest syntax</a>
 */
public class InitialRequestHandler implements MessageHandler {

    /**
     *<pre>
     * CertRequest ::= SEQUENCE {
     *      certReqId     INTEGER,        -- ID for matching request and reply
     *      certTemplate  CertTemplate, --Selected fields of cert to be issued
     * 	    controls      Controls OPTIONAL
     * } -- Attributes affecting issuance
     *
     * CertTemplate ::= SEQUENCE {
     *      version      [0] Version               OPTIONAL,
     * 		    version MUST be 2 if supplied. It SHOULD be omitted.
     * 	    serialNumber [1] INTEGER               OPTIONAL,
     * 		    serialNumber MUST be omitted. This field is assigned by the CA during certificate creation.
     * 		signingAlg   [2] AlgorithmIdentifier   OPTIONAL,
     * 		    signingAlg MUST be omitted. This field is assigned by the CA during certificate creation.
     * 		issuer       [3] Name                  OPTIONAL,
     * 		    issuer is normally omitted.  It would be filled in with the CA that the requestor
     * 		    desires to issue the certificate in situations where an RA is servicing more than one CA.
     * 		validity     [4] OptionalValidity      OPTIONAL,
     *          validity is normally omitted.  It can be used to request that
     * 		    certificates either start at some point in the future or expire at
     * 		    some specific time.  A case where this field would commonly be
     * 		    used is when a cross certificate is issued for a CA.  In this case
     * 			the validity of an existing certificate would be placed in this
     * 			field so that the new certificate would have the same validity
     * 			period as the existing certificate.  If validity is not omitted,
     * 			then at least one of the sub-fields MUST be specified.
     * 		subject      [5] Name                  OPTIONAL,
     * 		    subject is filled in with the suggested name for the requestor.
     * 			This would normally be filled in by a name that has been
     * 			previously issued to the requestor by the CA.
     * 		publicKey    [6] SubjectPublicKeyInfo  OPTIONAL,
     * 		issuerUID    [7] UniqueIdentifier      OPTIONAL,
     * 		subjectUID   [8] UniqueIdentifier      OPTIONAL,
     * 		extensions   [9] Extensions            OPTIONAL
     * }
     *
     * 			OptionalValidity ::= SEQUENCE {
     * 			  notBefore  [0] Time OPTIONAL,
     * 			  notAfter   [1] Time OPTIONAL } --at least one must be present
     *
     * 			Time ::= CHOICE {
     * 			  utcTime        UTCTime,
     * 			  generalTime    GeneralizedTime }
     *</pre>
     *
     * @param request
     * @return
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-5">...</a>
     */

    /*
			The device discovers the RA/CA address.

			The device generates the private/public key pair to be enrolled in the operator CA, if this is not pre-provisioned.

			The device generates the Initialization Request (IR). The CertReqMsg inside the request specifies the requested certificate. If the suggested identity is known to the device, it includes this in the subject field. To provide proof of possession, the device generates the signature for the POPOSigningKey field of the CertReqMsg using the private key related to the public key to be certified by the RA/CA. The device signs the request using the vendor provided public key, and includes the digital signature in the PKIMessage. Its own vendor signed certificate and any intermediate certificates are included in the extraCerts field of the PKIMessage carrying the initialization request.

			The device sends the signed initialization request message to the RA/CA.

			The RA/CA verifies the digital signature on the initialization request message against the vendor root certificate using the certificate(s) sent by the device. The RA/CA also verifies the proof of the possession of the private key for the requested certificate.

			The RA/CA generates the certificate for the device. If the suggested identity of the device is not included in the initialization request message, the RA/CA determines the suggested identity, based on the vendor provided identity contained in the device certificate. The RA/CA may also replace a suggested identity sent by the device with another identity based on local information.

			The RA/CA generates an Initialization Response (IP) which includes the issued certificate. The RA/CA signs the response with the RA/CA private key (or the private key for signing CMP messages, if separate), and includes the signature, the RA/CA certificate(s) and the operator root certificate in the PKIMessage. The appropriate certificate chains for authenticating the RA/CA certificate(s) are included in the PKIMessage.

			The RA/CA sends the signed initialization response to the device.

			If the operator root certificate is not pre-provisioned to the device, the device extracts the operator root certificate from the PKIMessage. The device authenticates the PKIMessage using the RA/CA certificate and installs the device certificate on success.

			The device creates and signs the CertificateConfirm (certconf) message.

			The device sends the PKIMessage that includes the signed CertificateConfirm to the RA/CA.

			The RA/CA authenticates the PKI Message that includes the CertificateConfirm.

			The RA/CA creates and signs a Confirmation message (pkiconf).

			The RA/CA sends the signed PKIMessage including the pkiconf message to the device.

			The device authenticates the pkiconf message.
			zdroj - https://ejbca.3key.company/ejbca/doc/Using_CMP_with_3GPP.html
     */
    @Override
    public PKIMessage handle(PKIMessage request) throws CmpException {
        PKIBody body = request.getBody();
        int requestBodyType = body.getType();
        CertReqMsg certReqMsg = ((CertReqMessages) body.getContent()).toCertReqMsgArray()[0];
        CertRequest certRequest = certReqMsg.getCertReq();
        CertTemplate certTemplate = certRequest.getCertTemplate();

        PKCSObjectIdentifiers p;
        ProofOfPossession popo = certReqMsg.getPop();

        // -- Proof-of-Possession validation
        new POPValidator()
                .validate(request);

        // -- PKI Message Protection
        new ProtectionValidator()
                .validate(request);

        throw new CmpException(PKIFailureInfo.badDataFormat, ImplFailureInfo.TODO);
    }

}

package com.czertainly.core.api.cmp.message.handler;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.PkiMessageDumper;
import com.czertainly.core.api.cmp.message.validator.impl.POPValidator;
import com.czertainly.core.api.cmp.mock.MockCaImpl;
import org.bouncycastle.asn1.cmp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
public class CrmfMessageHandler implements MessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CrmfMessageHandler.class.getName());
    private static final List<Integer> ALLOWED_TYPES = List.of(
            PKIBody.TYPE_INIT_REQ,          // ir       [0]  CertReqMessages,       --Initialization Req
            PKIBody.TYPE_CERT_REQ,          // cr       [2]  CertReqMessages,       --Certification Req
            PKIBody.TYPE_KEY_UPDATE_REQ,    // kur      [7]  CertReqMessages,       --Key Update Request
            PKIBody.TYPE_KEY_RECOVERY_REQ,  // krr      [9]  CertReqMessages,       --Key Recovery Req     (not implemented)
            PKIBody.TYPE_CROSS_CERT_REQ);   // ccr      [13] CertReqMessages,       --Cross-Cert.  Request (not implemented)

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
     * @param request crmf-base message as ir, cr, kur, krr, ccr
     * @return response message related <code>request</code> message
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-5">...</a>
     */
    @Override
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpException {
        if(!ALLOWED_TYPES.contains(request.getBody().getType())) {
            throw new CmpProcessingException(PKIFailureInfo.systemFailure, //system uses this handler bad way
                        "CRMF message cannot be handled, wrong message body/type, type="+request.getBody().getType());
        }
        new POPValidator(configuration)
                .validate(request);

        PKIMessage response = MockCaImpl
                .handleCrmfCertificateRequest(request, configuration);

        if(response != null) { return response; }
        throw new CmpProcessingException(
                PKIFailureInfo.systemFailure, "general problem while handling PKIMessage, type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()));
    }



}

package com.czertainly.core.service.cmp.profiles;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.api.cmp.error.CmpCrmfValidationException;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.CertificateKeyService;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.service.cmp.message.protection.impl.PasswordBasedMacProtectionStrategy;
import com.czertainly.core.service.cmp.message.protection.impl.SingatureBaseProtectionStrategy;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.service.cmp.util.CertUtil;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertReqMsg;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

import java.util.List;

public class Mobile3gppProfileContext implements ConfigurationContext {

    private final PKIMessage requestMessage;
    private final CmpProfile profile;
    private final CertificateKeyService certificateKeyService;
    private final List<RequestAttributeDto> issueAttributes;
    private final List<RequestAttributeDto> revokeAttributes;

    public Mobile3gppProfileContext(CmpProfile profile, PKIMessage pkiRequest,
                                    CertificateKeyService certificateKeyService,
                                    List<RequestAttributeDto> issueAttributes,
                                    List<RequestAttributeDto> revokeAttributes) {
        this.requestMessage =pkiRequest;
        this.profile = profile;
        this.certificateKeyService=certificateKeyService;
        this.issueAttributes = issueAttributes;
        this.revokeAttributes = revokeAttributes;
    }

    @Override
    public CmpProfile getProfile() { return profile; }

    /**
     * <b>scope: header template - response part</b>
     * @return pki header recipient
     */
    @Override
    public GeneralName getRecipient() { return null; /*requestMessage.getHeader().getRecipient();*/ }

    /**
     * <b>scope: header template - response part</b>
     * @return pki header recipient
     */
    @Override
    public ASN1OctetString getSenderKID() {
        ASN1OctetString senderKID = requestMessage.getHeader().getSenderKID();
        return senderKID == null ? new DEROctetString(new byte[0]) : senderKID;
    }

    /**
     * <p>The subject field of the CertTemplate shall contain the suggested name of the base
     * station if the base station has knowledge of it. Otherwise it shall be omitted.</p>
     *
     * <p>The publicKey field of the CertTemplate shall be mandatory and shall contain the public
     * key of the base station to be certified by the RA/CA. The private/public key pair may be
     * pre-provisioned to the base station, or generated inside the base station for the CMPv2
     * protocol run. The format of this field shall follow IETF RFC 5280 [14].
     * <p>
     * source: 9.5.4.2	Initialization Request
     * </p>
     *
     */
    @Override
    public void validateRequest(PKIMessage request) throws CmpProcessingException {
        ASN1OctetString tid = request.getHeader().getTransactionID();
        int bodyType = request.getBody().getType();
        switch (bodyType) {
            case PKIBody.TYPE_INIT_REQ:
                CertReqMessages content = (CertReqMessages) request.getBody().getContent();
                CertReqMsg[] certReqMsgs = content.toCertReqMsgArray();
                // All CMPv2 messages used within this profile shall consist of exactly one
                // PKIMessage, i.e. the size of the sequence for PKIMessages shall be 1 in
                // all cases.
                // see 9.5.2	Profile for the PKIMessage
                if(certReqMsgs.length > 1) {
                    throw new CmpCrmfValidationException(tid, bodyType,
                            PKIFailureInfo.badDataFormat, "only one certReqMsg is allowed");
                }
                /*
                 * <p>The publicKey field of the CertTemplate shall be mandatory and shall contain
                 * the public key of the base station to be certified by the RA/CA.
                 * The private/public key pair may be pre-provisioned to the base station,
                 * or generated inside the base station for the CMPv2 protocol run.
                 * </p>
                 *
                 * @param certReqMsgs which keeps related certificate
                 * @return public key wrapper
                 *
                 * @see 9.5.4.2	Initialization Request (chapter 9 Base Station), 3gpp 310
                 */
                CertTemplate certTemplate = certReqMsgs[0].getCertReq().getCertTemplate();
                SubjectPublicKeyInfo publicKey = certTemplate.getPublicKey();
                if(publicKey == null) {
                    throw new CmpCrmfValidationException(tid, bodyType,
                            PKIFailureInfo.badCertTemplate, "public key in template is null");
                }
            case PKIBody.TYPE_CERT_REQ:
            case PKIBody.TYPE_KEY_UPDATE_REQ:
                break;// do something
            default:
                throw new CmpProcessingException(tid,
                        PKIFailureInfo.badDataFormat, "only CRMF-based message can be validated");
        }
    }

    /**
     * <p>(3gpp) validation of crmf based response messages</p>
     *
     * <p>
     *     The support of the optional extraCerts field is required by this profile. The certificates within
     *     this field may be ordered in any order. The message-specific content of this field is specified
     *     in the subclause   9.5.4 in the profiling of the single PKI message bodies.
     * </p>
     * <p>
     * (3gpp) 9.5.4.3	Initialization Response</p>
     *
     * <p>
     *      The extraCerts field of the PKIMessage carrying the initialization response shall be mandatory and shall
     *      contain the operator root certificate and the RA/CA certificate (or certificates if separate private keys
     *      are used for signing of certificates and CMP messages). If the RA/CA certificate(s) are not signed by the
     *      operator root CA, also the intermediate certificates for the chain(s) up to the operator root certificate
     *      shall be included in the extraCerts field. If additional (self-signed) Root CA certificates are required,
     *      they shall be carried in the extraCerts field or caPubs field of the PKIMessage. Since extraCerts field is
     *      not under CMP message integrity protection, CMP over TLS should be used as a security transport mechanism.
     *      Since CMP already supports integrity protection for caPubs field, the use of security transport mechanisms
     *      is optional.</p>
     *
     */
    @Override
    public void validateResponse(PKIMessage response) throws CmpProcessingException {
        ASN1OctetString tid = response.getHeader().getTransactionID();
        switch (response.getBody().getType()) {
            case PKIBody.TYPE_INIT_REP:
            case PKIBody.TYPE_CERT_REP:
            case PKIBody.TYPE_KEY_UPDATE_REP:
                if(response.getExtraCerts() == null || response.getExtraCerts().length==0) {
                    throw new CmpProcessingException(tid,
                            PKIFailureInfo.badDataFormat, "field 'extraCerts' is null or empty");
                }
                break;// do something
            default:
                throw new CmpProcessingException(tid,
                        PKIFailureInfo.badDataFormat, "only CRMF-based message can be validated");
        }
    }

    @Override
    public ProtectionMethod getProtectionMethod() throws CmpConfigurationException {
        return getProfile().getRequestProtectionMethod();
        //return ProtectionMethod.SHARED_SECRET;
    }

    @Override
    public ProtectionStrategy getProtectionStrategy() throws CmpBaseException {
        ProtectionMethod czrtProtectionMethod = getProfile().getResponseProtectionMethod();
        //return ProtectionMethod.SHARED_SECRET;
        switch (czrtProtectionMethod){
            case SIGNATURE:
                return new SingatureBaseProtectionStrategy(this,
                    requestMessage.getHeader().getProtectionAlg(), certificateKeyService);
            case SHARED_SECRET:
                byte[] salt = CertUtil.generateRandomBytes(20);//precist z db
                int iterationCount = 1000;//precist z db
                return new PasswordBasedMacProtectionStrategy(this,
                        requestMessage.getHeader().getProtectionAlg(),
                        getSharedSecret(), salt, iterationCount);
            default:
                throw new CmpConfigurationException(requestMessage.getHeader().getTransactionID(),
                        PKIFailureInfo.systemFailure,
                        "wrong config3gppProfile: unknown type of protection strategy, type="+czrtProtectionMethod);
        }
    }// pri vyberu

    @Override
    public byte[] getSharedSecret() {
        /* senderKID field MUST hold an identifier
         *    that indicates to the receiver the appropriate shared secret
         *    information to use to verify the message */
        ASN1OctetString senderKID = requestMessage.getHeader().getSenderKID();//muze byt pouzit pro dohledani v db
        return profile.getSharedSecret().getBytes();
    }

    @Override
    public List<RequestAttributeDto> getClientOperationAttributes(boolean isRevoke) {
        return (isRevoke) ? revokeAttributes : issueAttributes;
    }
}

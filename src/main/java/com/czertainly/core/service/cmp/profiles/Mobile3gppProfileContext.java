package com.czertainly.core.service.cmp.profiles;

import com.czertainly.api.model.core.cmp.ProtectionMethod;
import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.api.cmp.error.CmpCrmfValidationException;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.service.cmp.message.protection.impl.PasswordBasedMacProtectionStrategy;
import com.czertainly.core.service.cmp.message.protection.impl.SingatureBaseProtectionStrategy;
import com.czertainly.core.service.cmp.mock.MockCaImpl;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.service.cmp.util.AlgorithmHelper;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertReqMsg;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.List;

public class Mobile3gppProfileContext implements ConfigurationContext {

    private final PKIMessage requestMessage;
    private final CmpProfile profile;
    private final Provider czertainlyProvider;
    private final CzertainlyPrivateKey signerPrivateKey;
    private final X509Certificate signerCertificate;

    public Mobile3gppProfileContext(CmpProfile profile, PKIMessage pkiRequest, Provider czertainlyProvider,
                                    CzertainlyPrivateKey signerPrivateKey, X509Certificate signerCertificate) {
        this.requestMessage =pkiRequest;
        this.profile = profile;
        this.czertainlyProvider=czertainlyProvider;
        this.signerPrivateKey = signerPrivateKey;
        this.signerCertificate = signerCertificate;
    }

    @Override
    public String getName() {
        return profile.getName();
    }

    @Override
    public CmpProfile getProfile() {
        return profile;
    }

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
     * <b>scope: header template - response part</b>
     * @return pki header recipient
     */
    @Override
    public GeneralName getRecipient() { return requestMessage.getHeader().getRecipient(); }

    /**
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
    private static SubjectPublicKeyInfo getPublicKey(CertReqMsg[] certReqMsgs) {
        CertTemplate certTemplate = certReqMsgs[0].getCertReq().getCertTemplate();
        return certTemplate.getPublicKey();
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
    public void validateCertReq(int bodyType, CertReqMessages content) throws CmpProcessingException {
        switch (bodyType) {
            case PKIBody.TYPE_INIT_REQ:
                CertReqMsg[] certReqMsgs = content.toCertReqMsgArray();
                // All CMPv2 messages used within this profile shall consist of exactly one
                // PKIMessage, i.e. the size of the sequence for PKIMessages shall be 1 in
                // all cases.
                // see 9.5.2	Profile for the PKIMessage
                if(certReqMsgs.length > 1) {
                    throw new CmpCrmfValidationException(bodyType,
                            PKIFailureInfo.badDataFormat, "only one certReqMsg is allowed");
                }
                SubjectPublicKeyInfo publicKey = getPublicKey(certReqMsgs);
                if(publicKey == null) {
                    throw new CmpCrmfValidationException(bodyType,
                            PKIFailureInfo.badCertTemplate, "public key in template is null");
                }
            case PKIBody.TYPE_CERT_REQ:
            case PKIBody.TYPE_KEY_UPDATE_REQ:
                break;// do something
            default:
                throw new CmpProcessingException(PKIFailureInfo.badDataFormat, "only CRMF-based message can be validated");
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
    public void validateCertRep(int bodyType, CertRepMessage content) throws CmpProcessingException {
        // TODO toce implementatce, viz. javadoc vyse
        switch (bodyType) {
            case PKIBody.TYPE_INIT_REP:
            case PKIBody.TYPE_CERT_REP:
            case PKIBody.TYPE_KEY_UPDATE_REP:
                break;// do something
            default:
                throw new CmpProcessingException(PKIFailureInfo.badDataFormat, "only CRMF-based message can be validated");
        }
    }

    @Override
    public ProtectionStrategy getProtectionStrategy() throws CmpBaseException {
        ProtectionMethod czrtProtectionMethod = getProfile().getResponseProtectionMethod();
        switch (getProfile().getResponseProtectionMethod()){
            case SIGNATURE: return new SingatureBaseProtectionStrategy(this);
            case SHARED_SECRET: return new PasswordBasedMacProtectionStrategy(this);
            default:
                throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                        "wrong config3gppProfile: unknown type of protection strategy, type="+czrtProtectionMethod);
        }
    }// pri vyberu

    /**
     * scope: PasswordBased-MAC
     * @return shared secret between client/czertainly server
     */
    @Override
    public byte[] getSharedSecret() {
        /* senderKID field MUST hold an identifier
         *    that indicates to the receiver the appropriate shared secret
         *    information to use to verify the message */
        ASN1OctetString senderKID = requestMessage.getHeader().getSenderKID();//muze byt pouzit pro dohledani v db
        return "1234-5678".getBytes();
    }

    /**
     * scope: PasswordBased-MAC
     * <p>salt contains a randomly generated value used in computing the key
     *       of the MAC process.  The salt SHOULD be at least 8 octets (64
     *       bits) long.</p>
     * @return at least 64 bits value for salting of password
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.1">Shared Secret Information</a>
     */
    public byte[] getSalt() { return "8765-4321".getBytes(); }

    /**
     * scope: PasswordBased-MAC Protection
     * @return count of iteration during counting of mac
     */
    @Override
    public int getIterationCount() { return 1000; }

    /**
     * scope: PasswordBased-MAC Protection
     * @return algorithm for digital digest (for PKI Protection field)
     * @throws CmpConfigurationException if algorithm cannot be found (e.g. wrong digest name).
     */
    @Override
    public AlgorithmIdentifier getDigestAlgorithm() throws CmpConfigurationException {
        PBMParameter pbmParameter = PBMParameter.getInstance(
                requestMessage.getHeader().getProtectionAlg().getParameters());
        AlgorithmIdentifier algorithmIdentifier = pbmParameter.getOwf();
        if(algorithmIdentifier == null) {
            algorithmIdentifier = DIGEST_ALGORITHM_IDENTIFIER_FINDER.find("SHA256");//db query/cmp profile.getSignatureName
            if(algorithmIdentifier == null) {
                throw new CmpConfigurationException(PKIFailureInfo.systemFailure, "wrong name of DIGEST algorithm");
            }
        }
        return algorithmIdentifier;
    }

    /**
     * scope: PasswordBased-MAC  Protection
     * @return algorithm for mac (for PKI Protection field)
     * @throws CmpConfigurationException if algorithm cannot be found (e.g. wrong mac name).
     */
    @Override
    public AlgorithmIdentifier getMacAlgorithm() throws CmpConfigurationException {
        PBMParameter pbmParameter = PBMParameter.getInstance(
                requestMessage.getHeader().getProtectionAlg().getParameters());
        AlgorithmIdentifier algorithmIdentifier = pbmParameter.getMac();
        if(algorithmIdentifier == null) {
            algorithmIdentifier = MAC_ALGORITHM_IDENTIFIER_FINDER.find("HMACSHA256");//db query/cmp profile.getSignatureName
            if(algorithmIdentifier == null) {
                throw new CmpConfigurationException(PKIFailureInfo.systemFailure, "wrong name of MAC algorithm");
            }
        }
        return algorithmIdentifier;
    }

    /**
     *  <b>scope: SignatureBased Protection</b>
     * @return private key for signature (for PKI Protection field)
     * @throws CmpConfigurationException if key cannot be found
     */
    @Override
    public PrivateKey getPrivateKeyForSigning() {
        return signerPrivateKey;//MockCaImpl.getPrivateKeyForSigning();
    }

    @Override
    public Provider getSignatureProvider() {
        return czertainlyProvider;
    }

    /**
     * <b>scope: SignatureBased Protection</b>
     * @return list of certificate, useful for signature-based protection
     */
    @Override
    public List<X509Certificate> getExtraCertsCertificateChain() {
        //return MockCaImpl.getChainOfIssuerCerts();
        return List.of(signerCertificate);
    }

    /**
     * <b>scope: SignatureBased Protection</b>
     * @return algorithm for signature (for PKI Protection field)
     * @throws CmpConfigurationException if algorithm cannot be found (e.g. wrong signature name).
     */
    @Override
    public AlgorithmHelper getSignatureAlgorithm() throws CmpConfigurationException {
        AlgorithmIdentifier algorithmIdentifier = requestMessage.getHeader().getProtectionAlg();
        if(algorithmIdentifier == null) {
            algorithmIdentifier = SIGNATURE_ALGORITHM_IDENTIFIER_FINDER.find("SHA256withECDSA");//db query/cmp profile.getSignatureName
            if(algorithmIdentifier == null) {
                throw new CmpConfigurationException(PKIFailureInfo.systemFailure, "wrong name of SIGNATURE algorithm");
            }
        }
        return new AlgorithmHelper(algorithmIdentifier);
    }
}

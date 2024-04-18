package com.czertainly.core.api.cmp.message;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.protection.ProtectionStrategy;
import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertReqMsg;
import org.bouncycastle.asn1.crmf.CertRequest;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.czertainly.core.api.cmp.message.util.NullUtil.*;

public class PkiMessageBuilder {

    /**
     * see rfc4210, D.1.4
     * <p>A "special" X.500 DN is called the "NULL-DN"; this means a DN
     *        containing a zero-length SEQUENCE OF RelativeDistinguishedNames
     *        (its DER encoding is then '3000'H).</p>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.1">General Rules for Interpretation of These Profiles at rfcx4210</a>
     */
    public static final GeneralName NULL_DN = new GeneralName(new X500Name(new RDN[0]));

    private final ConfigurationContext config;

    private PKIHeader pkiHeader;
    private PKIBody pkiBody;
    private ASN1BitString protection;
    private CMPCertificate[] extraCerts;
    private final ProtectionStrategy protectionStrategy;

    public PkiMessageBuilder(ConfigurationContext configuration){
        this.config = configuration;
        this.protectionStrategy = configuration.getProtectionStrategy();
    }

    public PkiMessageBuilder addHeader(PKIHeader requestHeader) {
        final GeneralName recipient = computeDefaultIfNull(config.getRecipient(), requestHeader::getRecipient);
        final GeneralName sender = computeDefaultIfNull(protectionStrategy.getSender(), requestHeader::getSender);
        PKIHeaderBuilder headerBuilder = new PKIHeaderBuilder(
                requestHeader.getPvno().intValueExact(),
                defaultIfNull(sender, NULL_DN),
                defaultIfNull(recipient, NULL_DN));
        headerBuilder.setMessageTime(requestHeader.getMessageTime());
        headerBuilder.setProtectionAlg(requestHeader.getProtectionAlg());
        headerBuilder.setSenderKID(requestHeader.getSenderKID());
        headerBuilder.setTransactionID(requestHeader.getTransactionID());
        headerBuilder.setSenderNonce(requestHeader.getSenderNonce());
        headerBuilder.setRecipNonce(requestHeader.getRecipNonce());
        headerBuilder.setGeneralInfo(requestHeader.getGeneralInfo());
        this.pkiHeader = headerBuilder.build();
        return this;
    }

    public PkiMessageBuilder addBody(PKIBody pkiBody) {
        this.pkiBody = pkiBody;
        return this;
    }

    public PkiMessageBuilder addExtraCerts(List<CMPCertificate> chainOfCertificates) throws Exception {
        List<CMPCertificate> chainOfIssuerCert=computeValueIfNotNull(chainOfCertificates,
                () -> new ArrayList<>(0));

        extraCerts = Stream.concat(
            defaultIfNull(protectionStrategy.getProtectingExtraCerts(), Collections.emptyList()).stream(),
            defaultIfNull(chainOfIssuerCert, Collections.emptyList()).stream()
        ).distinct()
        .toArray(CMPCertificate[]::new);
        return this;
    }

    public PKIMessage build() throws Exception {
        if(pkiHeader == null) {
            throw new CmpException(PKIFailureInfo.systemFailure, "response message cannot be without PKIHeader");
        }
        if(pkiBody == null) {
            throw new CmpException(PKIFailureInfo.systemFailure, "response message cannot be without PKIBody");
        }
        return new PKIMessage(
                pkiHeader,
                pkiBody,
                protectionStrategy.createProtection(pkiHeader, pkiBody),
                extraCerts.length == 0 ? null : extraCerts);
    }

    /**
     * generate {@link PKIBody} for types:
     * <ul>
     *     <li>{@link PKIBody#TYPE_INIT_REP} IP,</li>
     *     <li>{@link PKIBody#TYPE_CERT_REP} CP,</li>
     *     <li>{@link PKIBody#TYPE_KEY_UPDATE_REP} or KUP</li>
     *  </ul>
     *  with returning a certificate
     *
     * @param body        of message (supported only PKIBody.TYPE_INIT_REQ, PKIBody.TYPE_CERT_REQ or
     *                    *                    PKIBody.TYPE_KEY_UPDATE_REQ)
     * @param certificate the certificate to return
     * @param caPubs list of CA certifications
     * @return a IP, CP or KUP body
     * @throws CmpException if body is not (PKIBody.TYPE_INIT_REQ, PKIBody.TYPE_CERT_REQ or
     *                      PKIBody.TYPE_KEY_UPDATE_REQ)
     */
    public static PKIBody createIpCpKupBody(PKIBody body, CMPCertificate certificate,
                                            CMPCertificate[] caPubs) throws CmpException {
        int bodyType = body.getType();
        switch(bodyType){
            case PKIBody.TYPE_INIT_REQ:
            case PKIBody.TYPE_CERT_REQ:
            case PKIBody.TYPE_KEY_UPDATE_REQ:
                break;
            default:
                throw new CmpException(PKIFailureInfo.systemFailure, "cannot generated response for given type, type="+bodyType);
        }
        CertReqMsg certReqMsg = ((CertReqMessages) body.getContent()).toCertReqMsgArray()[0];
        CertRequest certRequest = certReqMsg.getCertReq();
        final CertResponse[] response = {
                new CertResponse(
                        certRequest.getCertReqId(),
                        new PKIStatusInfo(PKIStatus.granted),
                        new CertifiedKeyPair(new CertOrEncCert(certificate)),
                        null)
        };
        return new PKIBody(bodyType+1, new CertRepMessage(caPubs, response));
    }
}

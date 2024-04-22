package com.czertainly.core.api.cmp.message.builder;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.api.cmp.util.CertUtils;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertReqMsg;
import org.bouncycastle.asn1.crmf.CertRequest;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static com.czertainly.core.api.cmp.util.NullUtil.*;

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

    private PKIHeader pkiHeader;
    private PKIBody pkiBody;
    private CMPCertificate[] extraCerts;

    private final ConfigurationContext config;
    private final ProtectionStrategy protectionStrategy;

    public PkiMessageBuilder(ConfigurationContext configuration){
        this.config = configuration;
        this.protectionStrategy = configuration.getProtectionStrategy();
    }

    /**
     * <pre>
     *    PKIHeader ::= SEQUENCE {
     *          pvno                INTEGER     { cmp1999(1), cmp2000(2) },
     *          sender              GeneralName,
     *          recipient           GeneralName,
     *          messageTime     [0] GeneralizedTime         OPTIONAL,
     *          protectionAlg   [1] AlgorithmIdentifier     OPTIONAL,
     *          senderKID       [2] KeyIdentifier           OPTIONAL,
     *          recipKID        [3] KeyIdentifier           OPTIONAL,
     *          transactionID   [4] OCTET STRING            OPTIONAL,
     *          senderNonce     [5] OCTET STRING            OPTIONAL,
     *          recipNonce      [6] OCTET STRING            OPTIONAL,
     *          freeText        [7] PKIFreeText             OPTIONAL,
     *          generalInfo     [8] SEQUENCE SIZE (1..MAX) OF
     *                              InfoTypeAndValue     OPTIONAL
     *      }
     * </pre>
     * @param template
     * @return
     */
    public PkiMessageBuilder addHeader(HeaderTemplate template) {
        final GeneralName recipient = computeDefaultIfNull(config.getRecipient(), template::getRecipient);
        final GeneralName sender = computeDefaultIfNull(protectionStrategy.getSender(), template::getSender);

        PKIHeaderBuilder headerBuilder = new PKIHeaderBuilder(
                template.getPvno(),                                            // -- pvno         , int
                defaultIfNull(sender, NULL_DN),                                // -- sender       , GeneralName
                defaultIfNull(recipient, NULL_DN));                            // -- recipient    , GeneralName
        headerBuilder.setMessageTime(template.getMessageTime());               // -- messageTime  , GeneralizedTime
        headerBuilder.setProtectionAlg(protectionStrategy.getProtectionAlg()); // -- protectionAlg, AlgorithmIdentifier
        headerBuilder.setSenderKID(protectionStrategy.getSenderKID());         // -- senderKID    , KeyIdentifier
        // recipKID                                                            // -- recipKID     , KeyIdentifier
        headerBuilder.setTransactionID(template.getTransactionID());           // -- transactionID, OCTET STRING
        headerBuilder.setSenderNonce(template.getSenderNonce());               // -- senderNonce  , OCTET STRING
        headerBuilder.setRecipNonce(template.getRecipNonce());                 // -- recipNonce   , OCTET STRING
        // freeText                                                            // -- freeText     , OCTET STRING
        headerBuilder.setGeneralInfo(template.getGeneralInfo());               // -- generalInfo  , InfoTypeAndValue SEQUENCE (1..MAX)

        this.pkiHeader = headerBuilder.build();
        return this;
    }

    public PkiMessageBuilder addBody(PKIBody pkiBody) {
        this.pkiBody = pkiBody;
        return this;
    }

    /**
     * <p>The extraCerts field can contain certificates that may be useful to
     *      the recipient.  For example, this can be used by a CA or RA to
     *      present an end entity with certificates that it needs to verify its
     *      own new certificate (if, for example, the CA that issued the end
     *      entity's certificate is not a root CA for the end entity).  Note that
     *      this field does not necessarily contain a certification path; the
     *      recipient may have to sort, select from, or otherwise process the
     *      extra certificates in order to use them.</p>
     * Location:
     *      (optional) PKIMessage.caPubs (na urovni header/body)
     *
     * @param chainOfCertificates
     * @return builder itself
     * @throws Exception if any error occurs (filtering/listing of incoming certificates)
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1">PKI Message header</a>
     */
    public PkiMessageBuilder addExtraCerts(List<CMPCertificate> chainOfCertificates) throws Exception {
        extraCerts = Stream.concat(
            defaultIfNull(protectionStrategy.getProtectingExtraCerts(), Collections.emptyList()).stream(),
            defaultIfNull(chainOfCertificates, Collections.emptyList()).stream()
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
                (extraCerts == null || extraCerts.length == 0) ? null : extraCerts);
    }

    /******************************************************************************************************
      HELPER METHODS
     *******************************************************************************************************/

    /**
     * Basic (most message use this way) {@link PKIHeader} template object (it means that
     * it keeps or create pki header values for another processing).
     *
     * @param message ability to get/create value from given {@PKIMessage}
     * @return template object with values for {@PKIHeader}
     */
    public static HeaderTemplate buildBasicHeaderTemplate(PKIMessage message) {
        return new HeaderTemplate() {
            @Override
            public int getPvno() { return message.getHeader().getPvno().intValueExact(); }
            /**
             * The sender field contains the name of the sender of the PKIMessage.
             *    This name (in conjunction with senderKID, if supplied) should be
             *    sufficient to indicate the key to use to verify the protection on the
             *    message.  If nothing about the sender is known to the sending entity
             *    (e.g., in the init. req. message, where the end entity may not know
             *    its own Distinguished Name (DN), e-mail name, IP address, etc.), then
             *    the "sender" field MUST contain a "NULL" value; that is, the SEQUENCE
             *    OF relative distinguished names is of zero length.
             *
             * @return null
             *
             * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.1">[1]PKIHeader element</a>
             */
            @Override
            public GeneralName getSender() { return null; }

            /**
             * The senderNonce will typically be 128 bits of
             *      (pseudo-) random data generated by the sender...
             *      in the transaction.
             *
             * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.1">[1]PKIHeader element</a>
             */
            @Override
            public ASN1OctetString getSenderNonce() { return new DEROctetString(CertUtils.generateRandomBytes(16)); }

            /**
             * <p>The recipient field contains the name of the recipient of the
             *    PKIMessage.  This name (in conjunction with recipKID, if supplied)
             *    should be usable to verify the protection on the message.
             *    see, link [1] below.</p>
             *
             * <pre>
             * ir - CA name
             *      -- the name of the CA who is being asked to produce a certificate
             * ip - CA name
             *      -- the name of the CA who produced the message
             * certConf - CA name
             *      -- the name of the CA who was asked to produce a certificate
             * pkiConf - present
             *      -- sender name from certConf
             * see, link [2] below
             * </pre>
             *
             * @return name of recipient message
             *
             * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.1">[1]PKIHeader element</a>
             * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.4">[2]Initial Registration/Certification (Basic Authenticated Scheme)</a>
             */
            @Override
            public GeneralName getRecipient() { return message.getHeader().getSender(); }

            /**
             * whereas the recipNonce
             *    is copied from the senderNonce of the previous message in the
             *    transaction.
             *
             * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.1">[1]PKIHeader element</a>
             */
            @Override
            public ASN1OctetString getRecipNonce() { return message.getHeader().getSenderNonce(); }

            @Override
            public ASN1GeneralizedTime getMessageTime() { return new ASN1GeneralizedTime(new Date()); }

            @Override
            public ASN1OctetString getTransactionID() { return message.getHeader().getTransactionID(); }

            @Override
            public InfoTypeAndValue[] getGeneralInfo() { return message.getHeader().getGeneralInfo(); }
        };
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

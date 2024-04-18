package com.czertainly.core.api.cmp.message.protection;

import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;

import java.util.List;

public interface ProtectionStrategy {

    /**
     * (1 - password based protection) null
     * (2 - mac based protection) null
     *
     * get sender to use for protected message
     * @return sender to use for protected message
     */
    GeneralName getSender();

    /**
     * get protection algorithm
     * @return protection algorithm
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.2">Algorithm Use Profile</a>
     */
    AlgorithmIdentifier getProtectionAlg();

    /**
     * create {@link PKIMessage} protection string using {@link ProtectedPart}
     * <pre>
 *             ProtectedPart ::= SEQUENCE {
     *             header    PKIHeader,
     *             body      PKIBody
     *         }
     * </pre>
     *
     * @param header part for protection
     * @param body part for protection
     *
     * @return the protection string
     *
     * @throws Exception in case of error
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3">PKI Message Protection</a>
     */
    DERBitString createProtection(PKIHeader header, PKIBody body) throws Exception;

    List<CMPCertificate> getProtectingExtraCerts() throws Exception;
}

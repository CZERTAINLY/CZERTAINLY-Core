package com.czertainly.core.service.cmp.message;

import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.CertRepMessage;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultMacAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

public interface ConfigurationContext {

    DefaultSignatureAlgorithmIdentifierFinder SIGNATURE_ALGORITHM_IDENTIFIER_FINDER =
            new DefaultSignatureAlgorithmIdentifierFinder();
    DefaultDigestAlgorithmIdentifierFinder DIGEST_ALGORITHM_IDENTIFIER_FINDER =
            new DefaultDigestAlgorithmIdentifierFinder();
    DefaultMacAlgorithmIdentifierFinder MAC_ALGORITHM_IDENTIFIER_FINDER =
            new DefaultMacAlgorithmIdentifierFinder();

    /**
     * @return need for extraCerts (in response part)
     */
    List<X509Certificate> getExtraCertsCertificateChain();

    GeneralName getRecipient();

    /**
     * It allows to client define specified validation of CRMF request messages
     * @param bodyType which crmf type is handled
     * @param content of crmf based message
     */
    void validateCertReq(int bodyType, CertReqMessages content) throws CmpProcessingException;

    /**
     * It allows to client define specified validation of response messages (of CRMF request message)
     * @param bodyType which type is handled
     * @param content of message
     */
    void validateCertRep(int bodyType, CertRepMessage content) throws CmpProcessingException;

    String getName();

    ASN1OctetString getSenderKID();

    /* ------------------------------------------------------------------------------------ */
    //
    //  Protection config
    //
    /* ------------------------------------------------------------------------------------ */
    enum ProtectionType{
        SIGNATURE("signature"),SHARED_SECRET("shared_secret"),;
        private final String name;
        ProtectionType(String name) {this.name=name;}
        public String getName() { return name; }
    }
    /**
     * @return type of protection is allowed for incoming messages
     *          which is configured at czertainly server(profile)
     */
    ProtectionType getProtectionType();

    /**
     * @return get protection strategy (how message will be protected at response part
     *         which is configured at czertainly server(profile)
     */
    ProtectionStrategy getProtectionStrategy() throws CmpBaseException;

    /**
     * <b>scope: PASSWORD-BASED-MAC protection</b>
     *
     * @return digest algorithm for password-based-mac calculation (protection of response message)
     * @throws CmpConfigurationException if algorithm cannot be found
     */
    AlgorithmIdentifier getDigestAlgorithm() throws CmpConfigurationException;

    /**
     * <b>scope: PASSWORD-BASED-MAC protection</b>
     * @return identifier or mac algorithm
     * @throws CmpConfigurationException if algorithm cannot be found
     */
    AlgorithmIdentifier getMacAlgorithm() throws CmpConfigurationException;

    /**
     * <b>scope: PASSWORD-BASED-MAC protection</b>
     * <p>
     * in case of mac-base protection of message, shared-secret must exist in profile</p>
     *
     * <p>If nothing about the sender is known to the sending entity
     *    (e.g., in the init. req. message, where the end entity may not know
     *    its own Distinguished Name (DN), e-mail name, IP address, etc.), then
     *    the "sender" field MUST contain a "NULL" value; that is, the SEQUENCE
     *    OF relative distinguished names is of zero length.  In such a case,
     *    the senderKID field MUST hold an identifier (i.e., a reference
     *    number) that indicates to the receiver the appropriate shared secret
     *    information to use to verify the message.</p>
     *
     * <p>openssl scope:
     *      openssl cmp parameter '-secret', e.g. -secret pass:1234-5678
     * </p>
     *
     * @return shared-secret for given client/profile
     *
     * @see <a href="https://www.openssl.org/docs/manmaster/man1/openssl-passphrase-options.html">openssl-passphrase-options</a>
     */
    byte[] getSharedSecret();

    /**
     * <b>scope: PASSWORD-BASED-MAC protection</b>
     *
     * <p>iterationCount identifies the number of times the hash is applied
     *       during the key computation process.  The iterationCount MUST be a
     *       minimum of 100.  Many people suggest using values as high as 1000
     *       iterations as the minimum value.</p>
     *
     * @return value for salting of password (password-based-mac protection)
     */
    byte[] getSalt();

    /**
     * <b>scope: PASSWORD-BASED-MAC protection</b>
     *
     * @return count of iteration for calculate base key for MAC
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.1">Shared Secret Information</a>
     */
    int getIterationCount();

    /**
     * <b>scope: SIGNATURE-BASED protection</b>
     *
     * get private key related to end certificate
     * @return private key related to end certificate
     */
    PrivateKey getPrivateKeyForSigning();

    /**
     * <b>scope: SIGNATURE-BASED protection</b>
     *
     * @return get name of signature algorithm, which is configured at czertainly server
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html">Java Security Standard Algorithm Names Specification</a>
     */
    AlgorithmIdentifier getSignatureAlgorithm() throws CmpConfigurationException;
}

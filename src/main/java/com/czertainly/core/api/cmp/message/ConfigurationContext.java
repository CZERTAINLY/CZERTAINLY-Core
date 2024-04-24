package com.czertainly.core.api.cmp.message;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.protection.ProtectionStrategy;
import org.bouncycastle.asn1.cmp.CertRepMessage;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

public interface ConfigurationContext {

    DefaultSignatureAlgorithmIdentifierFinder SIGNATURE_ALGORITHM_FINDER = new DefaultSignatureAlgorithmIdentifierFinder();
    /**
     * get private key related to end certificate
     * @return private key related to end certificate
     */
    PrivateKey getPrivateKeyForSigning();

    /**
     * @return need for extraCerts (in response part)
     */
    List<X509Certificate> getExtraCertsCertificateChain();

    /**
     * Get signature for response part
     * - default: incoming from PKIHeader/xxx
     * - if default is null, need to define from czertainly TODO jak postavit AlgorithmIdentifier ze String-u
     * <p>
     * the standard name of the algorithm requested.
     * See the Signature section in the <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html">Java Security Standard Algorithm Names Specification</a>
     * for information about standard algorithm names.
     *
     * @return get name of signature algorithm, which is configured at czertainly server
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html">Java Security Standard Algorithm Names Specification</a>
     */
    AlgorithmIdentifier getSignatureAlgorithm() throws CmpException;

    GeneralName getRecipient();

    /**
     * It allows to client define specified validation of CRMF request messages
     * @param bodyType which crmf type is handled
     * @param content of crmf based message
     */
    void validateCertReq(int bodyType, CertReqMessages content) throws CmpException;

    /**
     * It allows to client define specified validation of response messages (of CRMF request message)
     * @param bodyType which type is handled
     * @param content of message
     */
    void validateCertRep(int bodyType, CertRepMessage content) throws CmpException;

    String getName();

    /**
     * in case of mac-base protection of message, shared-secret must exist in profile
     * @return shared-secret for given client/profile
     */
    byte[] getSharedSecret();

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
    ProtectionStrategy getProtectionStrategy() throws CmpException;
}

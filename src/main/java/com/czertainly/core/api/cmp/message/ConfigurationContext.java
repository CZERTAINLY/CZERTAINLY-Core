package com.czertainly.core.api.cmp.message;

import com.czertainly.core.api.cmp.message.protection.ProtectionStrategy;
import org.bouncycastle.asn1.x509.GeneralName;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

public interface ConfigurationContext {

    /**
     * get private key related to end certificate
     * @return private key related to end certificate
     */
    PrivateKey getPrivateKeyForSigning();

    List<X509Certificate> getCertificateChain();

    /**
     * the standard name of the algorithm requested.
     * See the Signature section in the <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html">Java Security Standard Algorithm Names Specification</a>
     * for information about standard algorithm names.
     *
     * @return get name of signature algorithm, which is configured at czertainly server
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html">Java Security Standard Algorithm Names Specification</a>
     */
    String getSignatureAlgorithmName();

    //new GeneralName(new X500Name(rec))
    GeneralName getRecipient();

    /**
     * @return get protection strategy (how message will be protected (request/response part))
     *         which is configured at czertainly server
     */
    ProtectionStrategy getProtectionStrategy();

    /**
     * @return if given implementation needs the Proof-Of-Possesion validation
     *
     * TODO [toce] 3gpp profile: there is a need POP validation, see 9.5.4.2	Initialization Request
     */
    boolean proofOfPossessionValidationNeeded();
}

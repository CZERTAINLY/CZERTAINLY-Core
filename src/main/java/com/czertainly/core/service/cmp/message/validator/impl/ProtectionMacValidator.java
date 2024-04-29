package com.czertainly.core.service.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.validator.Validator;
import com.czertainly.core.service.cmp.util.CryptoUtil;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Validator of Password-Based MAC protection of {@link PKIMessage}.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.1">Shared secret Information</a>
 */
public class ProtectionMacValidator implements Validator<PKIMessage, Void> {

    /**
     * see flow at rfc4210, section 5.1.3.1
     *      id-PasswordBasedMac OBJECT IDENTIFIER ::= {1 2 840 113533 7 66 13}
     *      PBMParameter ::= SEQUENCE {
     *        salt                OCTET STRING,
     *        owf                 AlgorithmIdentifier,
     *        iterationCount      INTEGER,
     *        mac                 AlgorithmIdentifier
     *      }
     * @param message subject (its protection and header) for validation
     * @return null if validation is ok
     * @throws CmpProcessingException if validation has failed
     */
    @Override
    public Void validate(PKIMessage message, ConfigurationContext configuration) throws CmpBaseException {
        ConfigurationContext.ProtectionType typeOfProtection = configuration.getProtectionType();
        if(ConfigurationContext.ProtectionType.SIGNATURE.equals(typeOfProtection)) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "wrong configuration: SIGNATURE is not setup");
        }
        try {
            PKIHeader header = message.getHeader();
            byte[] passwordAsBytes = configuration.getSharedSecret();
            PBMParameter pbmParameter = PBMParameter.getInstance(
                    header.getProtectionAlg().getParameters());      // -- PBMParameter
            byte[] salt = pbmParameter.getSalt().getOctets();        // --    salt (octetstring)
            AlgorithmIdentifier owf = pbmParameter.getOwf();         // --    owf  (algIdentifier)
            // The output of the final iteration (called
            //   "BASEKEY" for ease of reference, with a size of "H") is what is used
            //   to form the symmetric key.
            byte[] basekey = new byte[passwordAsBytes.length + salt.length];
            // The OWF is then applied iterationCount times, where
            //   the salted secret is the input to the first iteration and
            System.arraycopy(passwordAsBytes, 0, basekey, 0, passwordAsBytes.length);
            System.arraycopy(salt, 0, basekey, passwordAsBytes.length, salt.length);
            // for each
            //   successive iteration, the input is set to be the output of the
            //   previous iteration.  The output of the final iteration (called
            //   "BASEKEY" for ease of reference, with a size of "H")
            MessageDigest dig = MessageDigest.getInstance(owf.getAlgorithm().getId(),
                    CryptoUtil.getBouncyCastleProvider());
            for (int i = 0; i < pbmParameter.getIterationCount().getValue().intValue(); i++) {
                basekey = dig.digest(basekey);
                dig.reset();
            }
            // create mac instance
            String macId = pbmParameter.getMac().getAlgorithm().getId();
            Mac mac = Mac.getInstance(macId, CryptoUtil.getBouncyCastleProvider());
            mac.init(new SecretKeySpec(basekey, macId));
            mac.update(new ProtectedPart(header,
                    message.getBody()).getEncoded(ASN1Encoding.DER));
            // -- check counted bytes (mac) vs. bytes from protection field
            if (!Arrays.equals(mac.doFinal(), message.getProtection().getBytes())) {
                throw new CmpProcessingException(PKIFailureInfo.badMessageCheck,
                        "mac validation: check of PasswordBasedMac protection failed");
            }
        }
        catch (CmpBaseException e) { throw e; }
        catch (Exception e) {
            throw new CmpProcessingException(PKIFailureInfo.badMessageCheck,
                    e.getLocalizedMessage());
        }
        return null;// validation is ok
    }
}

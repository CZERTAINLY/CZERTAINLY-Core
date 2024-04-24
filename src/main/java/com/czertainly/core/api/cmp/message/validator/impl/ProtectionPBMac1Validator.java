package com.czertainly.core.api.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.validator.Validator;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.pkcs.PBMAC1Params;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.cmp.ProtectedPart;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO tocecz, najit v 3gpp naroky na tuto validaci
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8018">PKCS #5: Password-Based Cryptography Specification Version 2.1</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8018#section-7.1">7.1.1.  PBMAC1 Generation Operation</a>
 */
public class ProtectionPBMac1Validator implements Validator<PKIMessage, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(ProtectionPBMac1Validator.class.getName());

    private final ConfigurationContext configuration;

    public ProtectionPBMac1Validator(ConfigurationContext configuration){ this.configuration=configuration; }

    @Override
    public Void validate(PKIMessage message) throws CmpException {
        ConfigurationContext.ProtectionType typeOfProtection = configuration.getProtectionType();
        if(ConfigurationContext.ProtectionType.SIGNATURE.equals(typeOfProtection)) {
            throw new CmpException(PKIFailureInfo.systemFailure,
                    "wrong configuration: SIGNATURE is not setup");
        }
        throw new UnsupportedOperationException("not implemented yet");
    }
}

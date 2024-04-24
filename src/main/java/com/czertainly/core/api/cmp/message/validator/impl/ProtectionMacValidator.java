package com.czertainly.core.api.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.validator.Validator;
import com.czertainly.core.api.cmp.util.NullUtil;
import org.bouncycastle.asn1.cmp.PBMParameter;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * TODO tocecz, najit v 3gpp naroky na tuto validaci
 */
public class ProtectionMacValidator implements Validator<PKIMessage, Void> {

    private final ConfigurationContext configuration;

    public ProtectionMacValidator(ConfigurationContext configuration){ this.configuration=configuration; }

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

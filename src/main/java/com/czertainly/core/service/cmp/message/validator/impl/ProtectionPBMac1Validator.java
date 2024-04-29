package com.czertainly.core.service.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.validator.Validator;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;

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

    @Override
    public Void validate(PKIMessage message, ConfigurationContext configuration) throws CmpBaseException {
        ConfigurationContext.ProtectionType typeOfProtection = configuration.getProtectionType();
        if(ConfigurationContext.ProtectionType.SIGNATURE.equals(typeOfProtection)) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "wrong configuration: SIGNATURE is not setup");
        }
        throw new UnsupportedOperationException("not implemented yet");
    }
}

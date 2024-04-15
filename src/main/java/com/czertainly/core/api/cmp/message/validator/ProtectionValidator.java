package com.czertainly.core.api.cmp.message.validator;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.ImplFailureInfo;
import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.cmp.CMPObjectIdentifiers;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

public class ProtectionValidator implements Validator<PKIMessage, Void> {
    @Override
    public Void validate(PKIMessage message) throws CmpException {
        final ASN1BitString protection = message.getProtection();
        if (protection == null) {
            throw new CmpException(PKIFailureInfo.notAuthorized, ImplFailureInfo.CRYPTOPRO006);
        }
        final AlgorithmIdentifier protectionAlg = message.getHeader().getProtectionAlg();
        if (protectionAlg == null) {
            throw new CmpException(PKIFailureInfo.notAuthorized, ImplFailureInfo.CRYPTOPRO007);
        }
        if (CMPObjectIdentifiers.passwordBasedMac.equals(protectionAlg.getAlgorithm())) {
            //todo tocecz - password based mac validator
        } else if (PKCSObjectIdentifiers.id_PBMAC1.equals(protectionAlg.getAlgorithm())) {
            //todo tocecz - add id_PBMAC1 validator
        } else {
            //todo tocecz - add certificate/signature based validator
        }
        return null;
    }
}

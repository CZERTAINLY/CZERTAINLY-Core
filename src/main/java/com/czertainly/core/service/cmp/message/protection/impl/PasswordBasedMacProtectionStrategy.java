package com.czertainly.core.service.cmp.message.protection.impl;

import com.czertainly.core.api.cmp.error.CmpConfigurationException;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.protection.ProtectionStrategy;
import com.czertainly.core.service.cmp.util.CryptoUtil;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.Provider;
import java.util.List;

/**
 * Implementation of password-based protection of {@link PKIMessage}.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.3.1">Shared Secret Information, at rfc4210</a>
 */
public class PasswordBasedMacProtectionStrategy extends BaseProtectionStrategy implements ProtectionStrategy {

    private Mac mac;
    private AlgorithmIdentifier protectionAlgorithm;

    public PasswordBasedMacProtectionStrategy(ConfigurationContext configuration)
            throws CmpBaseException {
        super(configuration);
        byte[] sharedSecret = configuration.getSharedSecret();//RA's shared secret
        byte[] protectionSalt = configuration.getSalt();
        byte[] calculatingBaseKey = new byte[sharedSecret.length + protectionSalt.length];
        System.arraycopy(sharedSecret, 0, calculatingBaseKey, 0, sharedSecret.length);
        System.arraycopy(protectionSalt, 0, calculatingBaseKey, sharedSecret.length, protectionSalt.length);

        try {
            Provider bouncyCastleProvider = CryptoUtil.getBouncyCastleProvider();
            AlgorithmIdentifier digestAlgorithm = configuration.getDigestAlgorithm();
            MessageDigest digest = MessageDigest.getInstance(digestAlgorithm.getAlgorithm().getId(),bouncyCastleProvider);
            int iterationCount = configuration.getIterationCount();
            for (int i = 0; i < iterationCount; i++) {
                calculatingBaseKey = digest.digest(calculatingBaseKey);
                digest.reset();
            }

            AlgorithmIdentifier macAlgorithm = configuration.getMacAlgorithm();
            this.mac = Mac.getInstance(macAlgorithm.getAlgorithm().getId(),bouncyCastleProvider);
            this.mac.init(new SecretKeySpec(calculatingBaseKey, mac.getAlgorithm()));
            this.protectionAlgorithm = new AlgorithmIdentifier(
                    CMPObjectIdentifiers.passwordBasedMac,
                    new PBMParameter(
                            protectionSalt,
                            digestAlgorithm,
                            iterationCount,
                            macAlgorithm));
        } catch (Exception e) {
            throw new CmpProcessingException(PKIFailureInfo.systemFailure,
                    "cannot initialize of password based mac strategy");
        }
    }

    @Override
    public AlgorithmIdentifier getProtectionAlg() throws CmpConfigurationException {
        return protectionAlgorithm;
    }

    @Override
    public DERBitString createProtection(PKIHeader header, PKIBody body) throws IOException {
        mac.update(new ProtectedPart(header, body).getEncoded(ASN1Encoding.DER));
        byte[] protectionBytes = mac.doFinal();
        mac.reset();
        return new DERBitString(protectionBytes);
    }

    @Override
    public List<CMPCertificate> getProtectingExtraCerts() throws Exception { return null; }

    @Override
    public GeneralName getSender() { return null; }

    @Override
    public ASN1OctetString getSenderKID() { return configuration.getSenderKID(); }

}

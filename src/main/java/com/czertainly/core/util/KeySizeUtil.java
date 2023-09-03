package com.czertainly.core.util;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import org.bouncycastle.jce.provider.JCEECPublicKey;
import org.bouncycastle.pqc.jcajce.provider.dilithium.BCDilithiumPublicKey;
import org.bouncycastle.pqc.jcajce.provider.falcon.BCFalconPublicKey;
import org.bouncycastle.pqc.jcajce.provider.sphincsplus.BCSPHINCSPlusPublicKey;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.AbstractMap;
import java.util.Map;

public class KeySizeUtil {

    private KeySizeUtil() {
    }

    private static final Map<String, Integer> PQCPublicKeySize = Map.ofEntries(
            // FALCON Parameter Spec
            new AbstractMap.SimpleEntry<>("falcon-512", 7176),
            new AbstractMap.SimpleEntry<>("falcon-1024", 14344),
            //DILITHIUM Parameter Spec
            new AbstractMap.SimpleEntry<>("dilithium2", 10496),
            new AbstractMap.SimpleEntry<>("dilithium3", 15616),
            new AbstractMap.SimpleEntry<>("dilithium5", 20736),
            new AbstractMap.SimpleEntry<>("dilithium2-aes", 10496),
            new AbstractMap.SimpleEntry<>("dilithium3-aes", 15616),
            new AbstractMap.SimpleEntry<>("dilithium5-aes", 20736),
            // SPHINCS+ Parameter Spec
            new AbstractMap.SimpleEntry<>("sha2-128f-robust", 256),
            new AbstractMap.SimpleEntry<>("sha2-128s-robust", 256),
            new AbstractMap.SimpleEntry<>("sha2-192f-robust", 384),
            new AbstractMap.SimpleEntry<>("sha2-192s-robust", 384),
            new AbstractMap.SimpleEntry<>("sha2-256f-robust", 512),
            new AbstractMap.SimpleEntry<>("sha2-256s-robust", 512),
            new AbstractMap.SimpleEntry<>("sha2-128s-simple", 256),
            new AbstractMap.SimpleEntry<>("sha2-128f-simple", 256),
            new AbstractMap.SimpleEntry<>("sha2-192f-simple", 384),
            new AbstractMap.SimpleEntry<>("sha2-192s-simple", 384),
            new AbstractMap.SimpleEntry<>("sha2-256f-simple", 512),
            new AbstractMap.SimpleEntry<>("sha2-256s-simple", 512),
            new AbstractMap.SimpleEntry<>("shake-128f-robust", 256),
            new AbstractMap.SimpleEntry<>("shake-128s-robust", 256),
            new AbstractMap.SimpleEntry<>("shake-192f-robust", 384),
            new AbstractMap.SimpleEntry<>("shake-192s-robust", 384),
            new AbstractMap.SimpleEntry<>("shake-256f-robust", 512),
            new AbstractMap.SimpleEntry<>("shake-256s-robust", 512),
            new AbstractMap.SimpleEntry<>("shake-128f-simple", 256),
            new AbstractMap.SimpleEntry<>("shake-128s-simple", 256),
            new AbstractMap.SimpleEntry<>("shake-192f-simple", 384),
            new AbstractMap.SimpleEntry<>("shake-192s-simple", 384),
            new AbstractMap.SimpleEntry<>("shake-256f-simple", 512),
            new AbstractMap.SimpleEntry<>("shake-256s-simple", 512),
            new AbstractMap.SimpleEntry<>("haraka-128f-robust", 256),
            new AbstractMap.SimpleEntry<>("haraka-128s-robust", 256),
            new AbstractMap.SimpleEntry<>("haraka-256f-robust", 512),
            new AbstractMap.SimpleEntry<>("haraka-256s-robust", 512),
            new AbstractMap.SimpleEntry<>("haraka-192f-robust", 384),
            new AbstractMap.SimpleEntry<>("haraka-192s-robust", 384),
            new AbstractMap.SimpleEntry<>("haraka-128f-simple", 256),
            new AbstractMap.SimpleEntry<>("haraka-128s-simple", 256),
            new AbstractMap.SimpleEntry<>("haraka-192f-simple", 384),
            new AbstractMap.SimpleEntry<>("haraka-192s-simple", 384),
            new AbstractMap.SimpleEntry<>("haraka-256f-simple", 512),
            new AbstractMap.SimpleEntry<>("haraka-256s-simple", 512)
    );

    /**
     * Method to get the key length from the public Key.
     * For the Post Quantum Algorithm the key size is taken from the static map
     *
     * @param publicKey Public Key of the certificate
     * @return Key Size of the public Key
     */
    public static int getKeyLength(final PublicKey publicKey) {
        int len = -1;
        if (publicKey instanceof final RSAPublicKey rsapub) {
            len = rsapub.getModulus().bitLength();
        } else if (publicKey instanceof final JCEECPublicKey ecpriv) {
            final org.bouncycastle.jce.spec.ECParameterSpec spec = ecpriv.getParameters();
            if (spec != null) {
                len = spec.getN().bitLength();
            } else {
                // We support the key, but we don't know the key length
                len = 0;
            }
        } else if (publicKey instanceof final ECPublicKey ecpriv) {
            final java.security.spec.ECParameterSpec spec = ecpriv.getParams();
            if (spec != null) {
                len = spec.getOrder().bitLength(); // does this really return something we expect?
            } else {
                // We support the key, but we don't know the key length
                len = 0;
            }
        } else if (publicKey instanceof final DSAPublicKey dsapub) {
            if (dsapub.getParams() != null) {
                len = dsapub.getParams().getP().bitLength();
            } else {
                len = dsapub.getY().bitLength();
            }
        } else if (publicKey instanceof final BCFalconPublicKey falconPublicKey) {
            return getPQCKeySizeFromMap(falconPublicKey.getParameterSpec().getName());
        } else if (publicKey instanceof final BCSPHINCSPlusPublicKey sphincsPlusPublicKey) {
            return getPQCKeySizeFromMap(sphincsPlusPublicKey.getParameterSpec().getName());
        } else if (publicKey instanceof final BCDilithiumPublicKey dilithiumPublicKey) {
            return getPQCKeySizeFromMap(dilithiumPublicKey.getParameterSpec().getName());
        }
        return len;
    }

    /**
     * Function to get the key soze of the PQC based on the key algorithm name
     *
     * @param name Name of the algorithm
     * @return Public Key Size
     */
    private static Integer getPQCKeySizeFromMap(String name) {
        Integer publicKeySize = PQCPublicKeySize.get(name.toLowerCase());
        if (publicKeySize == null) {
            throw new ValidationException(
                    ValidationError.create(
                            "Post Quantum Public Key Algorithm " + name + " is not yet supported"
                    )
            );
        }
        return publicKeySize;
    }
}
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
import java.util.HashMap;
import java.util.Map;

public class KeySizeUtil {

    private static final Map<String, Integer> PQCPublicKeySize = new HashMap<>() {{
        // FALCON Parameter Spec
        put("falcon-512", 897);
        put("falcon-1024", 1793);
        //DILITHIUM Parameter Spec
        put("dilithium2", 1312);
        put("dilithium3", 1952);
        put("dilithium5", 2592);
        put("dilithium2-aes", 1312);
        put("dilithium3-aes", 1952);
        put("dilithium5-aes", 2592);
        // SPHINCS+ Parameter Spec
        put("sha2-128f-robust", 32);
        put("sha2-128s-robust", 32);
        put("sha2-192f-robust", 48);
        put("sha2-192s-robust", 48);
        put("sha2-256f-robust", 64);
        put("sha2-256s-robust", 64);
        put("sha2-128s-simple", 32);
        put("sha2-128f-simple", 32);
        put("sha2-192f-simple", 48);
        put("sha2-192s-simple", 48);
        put("sha2-256f-simple", 64);
        put("sha2-256s-simple", 64);
        put("shake-128f-robust", 32);
        put("shake-128s-robust", 32);
        put("shake-192f-robust", 48);
        put("shake-192s-robust", 48);
        put("shake-256f-robust", 64);
        put("shake-256s-robust", 64);
        put("shake-128f-simple", 32);
        put("shake-128s-simple", 32);
        put("shake-192f-simple", 48);
        put("shake-192s-simple", 48);
        put("shake-256f-simple", 64);
        put("shake-256s-simple", 64);
        put("haraka-128f-robust", 32);
        put("haraka-128s-robust", 32);
        put("haraka-256f-robust", 64);
        put("haraka-256s-robust", 64);
        put("haraka-192f-robust", 48);
        put("haraka-192s-robust", 48);
        put("haraka-128f-simple", 32);
        put("haraka-128s-simple", 32);
        put("haraka-192f-simple", 48);
        put("haraka-192s-simple", 48);
        put("haraka-256f-simple", 64);
        put("haraka-256s-simple", 64);
    }};

    /**
     * Method to get the key length from the public Key.
     * For the Post Quantum Algorithm the key size is taken from the static map
     *
     * @param publicKey Public Key of the certificate
     * @return Key Size of the public Key
     */
    public static int getKeyLength(final PublicKey publicKey) {
        int len = -1;
        if (publicKey instanceof RSAPublicKey) {
            final RSAPublicKey rsapub = (RSAPublicKey) publicKey;
            len = rsapub.getModulus().bitLength();
        } else if (publicKey instanceof JCEECPublicKey) {
            final JCEECPublicKey ecpriv = (JCEECPublicKey) publicKey;
            final org.bouncycastle.jce.spec.ECParameterSpec spec = ecpriv.getParameters();
            if (spec != null) {
                len = spec.getN().bitLength();
            } else {
                // We support the key, but we don't know the key length
                len = 0;
            }
        } else if (publicKey instanceof ECPublicKey) {
            final ECPublicKey ecpriv = (ECPublicKey) publicKey;
            final java.security.spec.ECParameterSpec spec = ecpriv.getParams();
            if (spec != null) {
                len = spec.getOrder().bitLength(); // does this really return something we expect?
            } else {
                // We support the key, but we don't know the key length
                len = 0;
            }
        } else if (publicKey instanceof DSAPublicKey) {
            final DSAPublicKey dsapub = (DSAPublicKey) publicKey;
            if (dsapub.getParams() != null) {
                len = dsapub.getParams().getP().bitLength();
            } else {
                len = dsapub.getY().bitLength();
            }
        } else if (publicKey instanceof BCFalconPublicKey) {
            return getPQCKeySizeFromMap(((BCFalconPublicKey) publicKey).getParameterSpec().getName());
        } else if (publicKey instanceof BCSPHINCSPlusPublicKey) {
            return getPQCKeySizeFromMap(((BCSPHINCSPlusPublicKey) publicKey).getParameterSpec().getName());
        } else if (publicKey instanceof BCDilithiumPublicKey) {
            return getPQCKeySizeFromMap(((BCDilithiumPublicKey) publicKey).getParameterSpec().getName());
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
        Integer publicKeySize = PQCPublicKeySize.get(name);
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
package com.czertainly.core.util;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyFormat;
import com.czertainly.api.model.common.enums.cryptography.RsaSignatureScheme;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.core.attribute.EcdsaSignatureAttributes;
import com.czertainly.core.attribute.RsaSignatureAttributes;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.mldsa.BCMLDSAPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.slhdsa.BCSLHDSAPublicKey;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.pqc.jcajce.provider.falcon.BCFalconPublicKey;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

public class CryptographyUtil {
    public static AlgorithmIdentifier prepareSignatureAlgorithm(KeyAlgorithm keyAlgorithm, String publicKey, List<RequestAttributeDto> signatureAttributes) {
        String signatureAlgorithm;

        switch (keyAlgorithm) {
            case RSA -> {
                final RsaSignatureScheme scheme = RsaSignatureScheme.findByCode(
                        AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                                        RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME, signatureAttributes, StringAttributeContent.class)
                                .getData()
                );
                final DigestAlgorithm digest = DigestAlgorithm.findByCode(
                        AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                                        RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST, signatureAttributes, StringAttributeContent.class)
                                .getData()
                );

                signatureAlgorithm = digest.getProviderName() + "WITHRSA";
                if (scheme == RsaSignatureScheme.PSS) {
                    signatureAlgorithm += "ANDMGF1";
                }

                return getAlgorithmIdentifierInstance(signatureAlgorithm);
            }
            case ECDSA -> {
                final DigestAlgorithm digest = DigestAlgorithm.findByCode(
                        AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                                        EcdsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST, signatureAttributes, StringAttributeContent.class)
                                .getData()
                );

                signatureAlgorithm = digest.getProviderName() + "WITHECDSA";

                return getAlgorithmIdentifierInstance(signatureAlgorithm);
            }
            case FALCON -> {
                try {
                    String algorithmName = new BCFalconPublicKey(
                            SubjectPublicKeyInfo.getInstance(
                                    Base64.getDecoder().decode(
                                            publicKey
                                    )
                            ))
                            .getParameterSpec()
                            .getName();
                    return getAlgorithmIdentifierInstance(algorithmName);
                } catch (IOException e) {
                    throw new ValidationException(
                            ValidationError.create(
                                    "Failed obtaining signature algorithm"
                            )
                    );
                }


            }
            case MLDSA -> {
                try {
                    String algorithmName = new BCMLDSAPublicKey(
                            SubjectPublicKeyInfo.getInstance(
                                    Base64.getDecoder().decode(
                                            publicKey
                                    )
                            ))
                            .getParameterSpec()
                            .getName();
                    return getAlgorithmIdentifierInstance(algorithmName);
                } catch (IOException e) {
                    throw new ValidationException(
                            ValidationError.create(
                                    "Failed obtaining signature algorithm"
                            )
                    );
                }
            }
            case SLHDSA -> {
                try {
                    String algorithmName = new BCSLHDSAPublicKey(
                            SubjectPublicKeyInfo.getInstance(
                                    Base64.getDecoder().decode(
                                            publicKey
                                    )
                            ))
                            .getParameterSpec()
                            .getName();
                    return getAlgorithmIdentifierInstance(algorithmName);
                } catch (IOException e) {
                    throw new ValidationException(
                            ValidationError.create(
                                    "Failed obtaining signature algorithm"
                            )
                    );
                }
            }
            default -> throw new ValidationException(
                    ValidationError.create(
                            "Cryptographic algorithm not supported"
                    )
            );
        }
    }

    public static AlgorithmIdentifier getAlgorithmIdentifierInstance(String algorithm) {
        return new DefaultSignatureAlgorithmIdentifierFinder().find(algorithm);
    }

    public static KeyFormat getPublicKeyFormat(byte[] encodedPublicKey) {
        try {
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(ASN1Primitive.fromByteArray(encodedPublicKey));
            return spki != null ? KeyFormat.SPKI : KeyFormat.RAW;
        } catch (IOException | IllegalArgumentException e) {
            return KeyFormat.RAW;
        }

    }
}

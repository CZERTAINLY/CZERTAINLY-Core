package com.czertainly.core.service.cmp.util;

import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

/**
 * Helper to translate ANS1 -> JAVA algorithm names
 */
public class AlgorithmHelper {

    private final AlgorithmIdentifier ans1AlgorithmIdentifier;

    public AlgorithmHelper(AlgorithmIdentifier ans1AlgorithmIdentifier) {
        this.ans1AlgorithmIdentifier = ans1AlgorithmIdentifier;
    }

    /**
     * ANS1 structure is differnet from java standard names, (X9.ecdsa_with_SHA256 (1.2.840.10045.4.3.2))
     *
     * @return name of algorithm as ANS1 Object Identifier structure, e.g. 1.2.840.10045.4.3.2
     */
    public AlgorithmIdentifier asANS1AlgorithmIdentifier() {
        return ans1AlgorithmIdentifier;
    }

    /**
     * @return name of algorithm from standard java algorithm names, e.g. SHA256withECDSA
     */
    public String asJcaName() {
        return OIDS.findMatch(ans1AlgorithmIdentifier.getAlgorithm().getId()).algName();
    }
}

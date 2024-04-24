package com.czertainly.core.api.cmp.message.protection.impl;

import com.czertainly.core.api.cmp.message.ConfigurationContext;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

public abstract class BaseProtectionStrategy {

    protected final ConfigurationContext configuration;
    protected final LinkedList<X509Certificate> certificationsChain;

    protected BaseProtectionStrategy(ConfigurationContext configuration) {
        this.configuration = configuration;
        this.certificationsChain = new LinkedList<>(configuration.getExtraCertsCertificateChain());
    }

    public X509Certificate getEndCertificate() {
        return getCertChain().get(0);
    }

    protected List<X509Certificate> getCertChain() {
        return certificationsChain;
    }
}

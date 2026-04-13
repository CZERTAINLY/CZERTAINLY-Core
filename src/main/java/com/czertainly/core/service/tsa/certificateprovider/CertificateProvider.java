package com.czertainly.core.service.tsa.certificateprovider;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.service.tsa.CertificateChain;

/**
 * Builds the {@link CertificateChain} used to sign and assemble timestamp tokens,
 * and optionally validates the signer certificate against workflow requirements.
 */
public interface CertificateProvider {

    boolean supports(SigningSchemeModel signingScheme);

    /**
     * Validates the signer certificate against the signing workflow requirements.
     */
    void validate(SigningSchemeModel signingScheme, boolean qualifiedTimestamp) throws TspException;

    CertificateChain getCertificateChain(SigningSchemeModel signingScheme) throws TspException;
}

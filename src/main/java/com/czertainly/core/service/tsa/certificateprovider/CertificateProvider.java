package com.czertainly.core.service.tsa.certificateprovider;


import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.service.tsa.CertificateChain;

/**
 * Provides the signer certificate chain used to sign timestamp tokens.
 */
public interface CertificateProvider {

    boolean supports(SigningSchemeModel signingScheme);

    CertificateChain getCertificateChain(SigningSchemeModel signingScheme) throws TspException;
}

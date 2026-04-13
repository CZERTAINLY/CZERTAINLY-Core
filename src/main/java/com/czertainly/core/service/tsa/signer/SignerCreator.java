package com.czertainly.core.service.tsa.signer;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;

/**
 * Creates a {@link Signer} for a specific type of signing scheme.
 * Each implementation handles one scheme type and declares support via {@link #supports}.
 */
public interface SignerCreator {

    boolean supports(SigningSchemeModel signingScheme);

    Signer create(SigningSchemeModel signingScheme) throws TspException;
}

package com.czertainly.core.service.tsa.signer;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.model.client.signing.profile.scheme.SigningSchemeDto;

/**
 * Creates a {@link Signer} for a specific type of signing scheme.
 * Each implementation handles one scheme type and declares support via {@link #supports}.
 */
public interface SignerCreator {

    boolean supports(SigningSchemeDto signingScheme);

    Signer create(SigningSchemeDto signingScheme) throws TspException;
}
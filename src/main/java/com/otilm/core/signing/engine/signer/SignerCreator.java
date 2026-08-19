package com.otilm.core.signing.engine.signer;

import com.otilm.core.model.signing.resolved.ResolvedManagedScheme;
import com.otilm.core.signing.engine.error.SigningEngineException;

/**
 * Creates a {@link Signer} for a specific type of signing scheme. Each implementation handles one scheme type and
 * declares support via {@link #supports}.
 */
public interface SignerCreator {

    boolean supports(ResolvedManagedScheme signingScheme);

    Signer create(ResolvedManagedScheme signingScheme) throws SigningEngineException;
}

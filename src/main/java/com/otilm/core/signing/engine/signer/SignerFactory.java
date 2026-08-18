package com.otilm.core.signing.engine.signer;

import com.otilm.core.model.signing.resolved.ResolvedManagedScheme;
import com.otilm.core.signing.engine.error.SigningEngineException;
import com.otilm.core.signing.engine.error.SigningEngineFailure;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Selects the appropriate {@link SignerCreator} for a given signing scheme.
 */
@Component
public class SignerFactory {

    private final List<SignerCreator> creators;

    public SignerFactory(List<SignerCreator> creators) {
        this.creators = creators;
    }

    public Signer create(ResolvedManagedScheme signingScheme) throws SigningEngineException {
        return creators
                .stream()
                .filter(c -> c.supports(signingScheme))
                .findFirst()
                .orElseThrow(() -> new SigningEngineException(SigningEngineFailure.MISCONFIGURED,
                        "No SignerCreator supports signing scheme '%s'"
                                .formatted(signingScheme.getClass().getSimpleName()),
                        "The system is misconfigured."))
                .create(signingScheme);
    }
}

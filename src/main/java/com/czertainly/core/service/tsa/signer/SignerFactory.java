package com.czertainly.core.service.tsa.signer;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the appropriate {@link SignerCreator} for a given signing scheme.
 */
@Component
public class SignerFactory {

    private final List<SignerCreator> creators;

    public SignerFactory(List<SignerCreator> creators) {
        this.creators = creators;
    }

    public Signer create(SigningSchemeModel signingScheme) throws TspException {
        return creators.stream()
                .filter(c -> c.supports(signingScheme))
                .findFirst()
                .orElseThrow(() -> new TspException(
                        TspFailureInfo.SYSTEM_FAILURE,
                        "No SignerCreator supports signing scheme '%s'".formatted(
                                signingScheme.getClass().getSimpleName()),
                        "The system is misconfigured."))
                .create(signingScheme);
    }
}

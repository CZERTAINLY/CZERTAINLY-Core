package com.czertainly.core.model.signing.scheme;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.core.dao.entity.Certificate;

import java.util.List;

/**
 * Scheme model for managed signing using a pre-existing static certificate and key pair.
 *
 * @param certificate                  The certificate and associated key(s) used for signing.
 * @param signingOperationAttributes   Attributes required for signing operations (such as digest algorithm).
 */
public record StaticKeyManagedSigning(
        Certificate certificate,
        List<RequestAttribute> signingOperationAttributes
) implements ManagedSigning {

    @Override
    public ManagedSigningType getManagedSigningType() {
        return ManagedSigningType.STATIC_KEY;
    }
}

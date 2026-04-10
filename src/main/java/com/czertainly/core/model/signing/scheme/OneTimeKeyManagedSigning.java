package com.czertainly.core.model.signing.scheme;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.TokenProfile;

import java.util.List;
import java.util.UUID;

/**
 * Scheme model for managed signing using a freshly issued one-time certificate and key pair.
 *
 * @param raProfile                   RA Profile used to issue the one-time signing certificate.
 * @param tokenProfile                Token Profile used to store and manage the issued certificate and key pair.
 * @param csrTemplateUuid             UUID of the CSR Template used for the certificate issuance request.
 * @param signingOperationAttributes  Attributes required for signing operations (such as digest algorithm).
 */
public record OneTimeKeyManagedSigning(
        RaProfile raProfile,
        TokenProfile tokenProfile,
        UUID csrTemplateUuid,
        List<RequestAttribute> signingOperationAttributes
) implements ManagedSigning {

    @Override
    public ManagedSigningType getManagedSigningType() {
        return ManagedSigningType.ONE_TIME_KEY;
    }
}

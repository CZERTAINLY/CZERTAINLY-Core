package com.czertainly.core.provisioning;

import jakarta.validation.constraints.NotNull;

/**
 * Request for creating a new trusted certificate in provisioning service.
 *
 * @param certificateContent Base64 encoded certificate content
 */
public record TrustedCertificateProvisioningRequestDTO(
        @NotNull
        byte[] certificateContent
) {
}

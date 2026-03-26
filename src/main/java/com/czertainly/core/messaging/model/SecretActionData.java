package com.czertainly.core.messaging.model;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record SecretActionData(
    String encryptedContent,
    String name,
    UUID updatedSourceVaultProfileUuid,
    List<RequestAttribute> attributes
) {
}

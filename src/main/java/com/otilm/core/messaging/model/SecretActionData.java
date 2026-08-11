package com.otilm.core.messaging.model;

import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.core.secret.SecretState;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SecretActionData(String encryptedContent, String name, UUID updatedSourceVaultProfileUuid,
        List<RequestAttribute> attributes, boolean deleteInVault, SecretState originalState) {
}

package com.otilm.core.util.builders;

import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.client.signing.profile.workflow.DocumentSigningWorkflowRequestDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DocumentSigningWorkflowRequestDtoBuilder {

    private UUID signatureFormattingConnectorUuid = null;
    private List<RequestAttribute> signatureFormattingConnectorAttributes = new ArrayList<>();

    public static DocumentSigningWorkflowRequestDtoBuilder aDocumentSigningWorkflow() {
        return new DocumentSigningWorkflowRequestDtoBuilder();
    }

    public DocumentSigningWorkflowRequestDtoBuilder withSignatureFormattingConnector(UUID uuid) {
        this.signatureFormattingConnectorUuid = uuid;
        return this;
    }

    public DocumentSigningWorkflowRequestDtoBuilder withSignatureFormattingConnectorAttributes(
            List<RequestAttribute> attrs) {
        this.signatureFormattingConnectorAttributes = attrs;
        return this;
    }

    public DocumentSigningWorkflowRequestDto build() {
        DocumentSigningWorkflowRequestDto dto = new DocumentSigningWorkflowRequestDto();
        dto.setSignatureFormattingConnectorUuid(signatureFormattingConnectorUuid);
        dto.setSignatureFormattingConnectorAttributes(signatureFormattingConnectorAttributes);
        return dto;
    }
}

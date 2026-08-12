package com.otilm.core.model.signing.workflow;

import com.otilm.api.model.client.attribute.RequestAttribute;

import java.util.List;
import java.util.UUID;

/**
 * Document-signing workflow for ILM-managed signing.
 *
 * @param signatureFormattingConnectorUuid UUID of the Signature Formatting Provider.
 * @param signatureFormattingConnectorAttributes Attributes controlling DTBS construction.
 */
public record ManagedDocumentSigningWorkflow(UUID signatureFormattingConnectorUuid,
        List<RequestAttribute> signatureFormattingConnectorAttributes) implements DocumentSigningWorkflow {
}

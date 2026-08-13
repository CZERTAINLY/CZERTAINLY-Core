package com.otilm.core.model.signing.workflow;

import com.otilm.api.model.client.signing.profile.workflow.SigningWorkflowType;

/**
 * Sealed interface for the {@code DOCUMENT_SIGNING} workflow model.
 *
 * <p>
 * Document signing has no common fields shared between managed and delegated signing.
 * </p>
 */
public sealed interface DocumentSigningWorkflow extends SigningWorkflow
        permits ManagedDocumentSigningWorkflow, DelegatedDocumentSigningWorkflow {

    @Override
    default SigningWorkflowType getWorkflowType() {
        return SigningWorkflowType.DOCUMENT_SIGNING;
    }
}

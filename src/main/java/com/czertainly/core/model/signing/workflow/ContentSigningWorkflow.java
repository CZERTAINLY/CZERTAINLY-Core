package com.czertainly.core.model.signing.workflow;

import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;

/**
 * Sealed interface for the {@code CONTENT_SIGNING} workflow model.
 *
 * <p>Content signing has no common fields shared between managed and delegated signing.
 *
 * <p>Use pattern matching to access managed-only fields:</p>
 * <pre>{@code
 * switch (profile.getWorkflow()) {
 *     case ManagedContentSigningWorkflow m -> m.signatureFormatterConnectorUuid();
 *     case DelegatedContentSigningWorkflow d -> { /* formatter not available *\/ }
 * }
 * }</pre>
 */
public sealed interface ContentSigningWorkflow extends SigningWorkflow
        permits ManagedContentSigningWorkflow, DelegatedContentSigningWorkflow {

    @Override
    default SigningWorkflowType getWorkflowType() {
        return SigningWorkflowType.CONTENT_SIGNING;
    }
}

package com.otilm.core.signing.engine.error;

/**
 * Workflow-agnostic failure taxonomy for the common Signing Engine. Each protocol edge maps these onto its own
 * currency.
 */
public enum SigningEngineFailure {

    /** The caller supplied something the profile or the contract does not permit. */
    INVALID_INPUT,

    /** The caller's data is structurally wrong. */
    MALFORMED_INPUT,

    /** The platform's own configuration is wrong. */
    MISCONFIGURED,

    /** A connector was reached but did not deliver. */
    CONNECTOR_FAULT,

    /** The signing key or the cryptography provider failed to produce a signature. */
    SIGNER_FAULT,

    /** A named step of a multi-step run failed. */
    STEP_FAILED
}

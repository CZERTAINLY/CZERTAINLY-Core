package com.czertainly.core.model.signing.workflow;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationDto;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;

import java.util.List;
import java.util.UUID;

/**
 * Timestamping workflow for ILM-managed signing.
 *
 * <p>Carries all common validation fields (inherited via {@link TimestampingWorkflow}) plus
 * the managed-signing-only fields.</p>
 *
 * @param signatureFormatterConnectorUuid       UUID of the Signature Formatter Connector that
 *                                              constructs the DTBS for Timestamping.
 * @param signatureFormatterConnectorAttributes Attributes controlling DTBS construction on the
 *                                              Signature Formatter Connector.
 * @param qualifiedTimestamp                    ETSI qualified electronic timestamp flag.
 * @param timeQualityConfiguration              Time Quality Configuration validating clock
 *                                              accuracy at signing time; required when
 *                                              {@code qualifiedTimestamp} is {@code true}
 *                                              (ETSI EN 319 421).
 * @param defaultPolicyId                       Default TSA Policy ID (OID format).
 * @param allowedPolicyIds                      Accepted TSA Policy IDs (OID format).
 * @param allowedDigestAlgorithms               Accepted digest algorithms; empty means all.
 * @param validateTokenSignature                Whether to validate the token signature after issuance.
 */
public record ManagedTimestampingWorkflow(
        UUID signatureFormatterConnectorUuid,
        List<RequestAttribute> signatureFormatterConnectorAttributes,
        Boolean qualifiedTimestamp,
        TimeQualityConfigurationDto timeQualityConfiguration,
        String defaultPolicyId,
        List<String> allowedPolicyIds,
        List<DigestAlgorithm> allowedDigestAlgorithms,
        Boolean validateTokenSignature
) implements TimestampingWorkflow {
}

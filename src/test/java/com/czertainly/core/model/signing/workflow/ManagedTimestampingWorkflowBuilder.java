package com.czertainly.core.model.signing.workflow;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;

import java.util.List;
import java.util.UUID;

public final class ManagedTimestampingWorkflowBuilder {

    private UUID signatureFormatterConnectorUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private List<RequestAttribute> signatureFormatterConnectorAttributes = List.of();
    private Boolean isQualifiedTimestamp = false;
    private TimeQualityConfigurationModel timeQualityConfiguration = null;
    private String defaultPolicyId = "1.2.3.4.5";
    private List<String> allowedPolicyIds = List.of();
    private List<DigestAlgorithm> allowedDigestAlgorithms = List.of();
    private Boolean validateTokenSignature = false;

    public static ManagedTimestampingWorkflowBuilder aManagedTimestampingWorkflow() {
        return new ManagedTimestampingWorkflowBuilder();
    }

    static ManagedTimestampingWorkflow<TimeQualityConfigurationModel> valid() {
        return aManagedTimestampingWorkflow().build();
    }

    public ManagedTimestampingWorkflowBuilder signatureFormatterConnectorUuid(UUID signatureFormatterConnectorUuid) {
        this.signatureFormatterConnectorUuid = signatureFormatterConnectorUuid;
        return this;
    }

    public ManagedTimestampingWorkflowBuilder signatureFormatterConnectorAttributes(List<RequestAttribute> signatureFormatterConnectorAttributes) {
        this.signatureFormatterConnectorAttributes = signatureFormatterConnectorAttributes;
        return this;
    }

    public ManagedTimestampingWorkflowBuilder isQualifiedTimestamp(Boolean isQualifiedTimestamp) {
        this.isQualifiedTimestamp = isQualifiedTimestamp;
        return this;
    }

    public ManagedTimestampingWorkflowBuilder timeQualityConfiguration(TimeQualityConfigurationModel timeQualityConfiguration) {
        this.timeQualityConfiguration = timeQualityConfiguration;
        return this;
    }

    public ManagedTimestampingWorkflowBuilder defaultPolicyId(String defaultPolicyId) {
        this.defaultPolicyId = defaultPolicyId;
        return this;
    }

    public ManagedTimestampingWorkflowBuilder allowedPolicyIds(List<String> allowedPolicyIds) {
        this.allowedPolicyIds = allowedPolicyIds;
        return this;
    }

    public ManagedTimestampingWorkflowBuilder allowedDigestAlgorithms(List<DigestAlgorithm> allowedDigestAlgorithms) {
        this.allowedDigestAlgorithms = allowedDigestAlgorithms;
        return this;
    }

    public ManagedTimestampingWorkflowBuilder validateTokenSignature(Boolean validateTokenSignature) {
        this.validateTokenSignature = validateTokenSignature;
        return this;
    }

    public ManagedTimestampingWorkflow<TimeQualityConfigurationModel> build() {
        return new ManagedTimestampingWorkflow<>(
                signatureFormatterConnectorUuid,
                signatureFormatterConnectorAttributes,
                isQualifiedTimestamp,
                timeQualityConfiguration,
                defaultPolicyId,
                allowedPolicyIds,
                allowedDigestAlgorithms,
                validateTokenSignature
        );
    }
}

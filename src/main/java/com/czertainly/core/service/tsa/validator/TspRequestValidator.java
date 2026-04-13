package com.czertainly.core.service.tsa.validator;

import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.core.model.signing.workflow.TimestampingWorkflow;
import com.czertainly.core.service.tsa.messages.TspRequest;
import org.springframework.stereotype.Component;

@Component
public class TspRequestValidator {

    public void validate(TimestampingWorkflow timestampingWorkflow, TspRequest request) throws TspRequestValidationException {
        validateExtensions(request);
        validateHashAlgorithm(timestampingWorkflow, request);
        validatePolicy(timestampingWorkflow, request);
    }

    private void validateExtensions(TspRequest request) throws TspRequestValidationException {
        if (request.requestExtensions() != null) {
            throw new TspRequestValidationException(
                    TspFailureInfo.UNACCEPTED_EXTENSION,
                    "Extensions are not supported by the chosen profile",
                    "Request contains extensions, but no extensions are configured as allowed by the profile");
        }
    }

    private void validateHashAlgorithm(TimestampingWorkflow timestampingWorkflow, TspRequest request) throws TspRequestValidationException {
        var allowed = timestampingWorkflow.allowedDigestAlgorithms();
        if (!allowed.isEmpty() && !allowed.contains(request.hashAlgorithm())) {
            throw new TspRequestValidationException(
                    TspFailureInfo.BAD_ALG,
                    "Hash algorithm is not accepted by the chosen profile",
                    "Hash algorithm '%s' is not accepted by the profile".formatted(request.hashAlgorithm().getCode()));
        }
    }

    private void validatePolicy(TimestampingWorkflow timestampingWorkflow, TspRequest request) throws TspRequestValidationException {
        if (timestampingWorkflow.allowedPolicyIds().isEmpty()) {
            return;
        }

        if (request.policy().isEmpty()) {
            return;
        }
        var requestedPolicy = request.policy().get();
        if (!timestampingWorkflow.allowedPolicyIds().contains(requestedPolicy)) {
            throw new TspRequestValidationException(
                    TspFailureInfo.UNACCEPTED_POLICY,
                    "Policy ID is not accepted by the chosen profile",
                    "Policy '%s' is not accepted by the profile".formatted(requestedPolicy));
        }
    }

}

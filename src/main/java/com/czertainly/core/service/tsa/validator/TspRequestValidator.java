package com.czertainly.core.service.tsa.validator;

import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.core.service.tsa.messages.TspRequest;
import org.springframework.stereotype.Component;

@Component
public class TspRequestValidator {

    public void validate(TimestampingWorkflowDto profile, TspRequest request) throws TspRequestValidationException {
        validateExtensions(request);
        validateHashAlgorithm(profile, request);
        validatePolicy(profile, request);
    }

    private void validateExtensions(TspRequest request) throws TspRequestValidationException {
        if (request.requestExtensions() != null) {
            throw new TspRequestValidationException(
                    TspFailureInfo.UNACCEPTED_EXTENSION,
                    "Extensions are not supported by the chosen profile",
                    "Request contains extensions, but no extensions are configured as allowed by the profile");
        }
    }

    private void validateHashAlgorithm(TimestampingWorkflowDto profile, TspRequest request) throws TspRequestValidationException {
        var allowed = profile.getAllowedDigestAlgorithms();
        if (!allowed.isEmpty() && !allowed.contains(request.hashAlgorithm())) {
            throw new TspRequestValidationException(
                    TspFailureInfo.BAD_ALG,
                    "Hash algorithm is not accepted by the chosen profile",
                    "Hash algorithm '%s' is not accepted by the profile".formatted(request.hashAlgorithm().getCode()));
        }
    }

    private void validatePolicy(TimestampingWorkflowDto profile, TspRequest request) throws TspRequestValidationException {
        if (profile.getAllowedPolicyIds().isEmpty()) {
            return;
        }

        if (request.policy().isEmpty()) {
            return;
        }
        var requestedPolicy = request.policy().get();
        if (!profile.getAllowedPolicyIds().contains(requestedPolicy)) {
            throw new TspRequestValidationException(
                    TspFailureInfo.UNACCEPTED_POLICY,
                    "Policy ID is not accepted by the chosen profile",
                    "Policy '%s' is not accepted by the profile".formatted(requestedPolicy));
        }
    }

}

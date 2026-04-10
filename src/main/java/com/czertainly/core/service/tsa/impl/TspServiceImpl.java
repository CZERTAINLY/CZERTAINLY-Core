package com.czertainly.core.service.tsa.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SimplifiedSigningProfileDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.WorkflowDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspProfileDto;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.TspProfileService;
import com.czertainly.core.service.tsa.TimestampEngine;
import com.czertainly.core.service.tsa.TspService;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.messages.TspResponse;
import com.czertainly.core.service.tsa.validator.TspRequestValidator;
import org.springframework.stereotype.Service;

@Service
public class TspServiceImpl implements TspService {

    private final TspRequestValidator tspRequestValidator;
    private final TspProfileService tspProfileService;
    private final SigningProfileService signingProfileService;
    private final TimestampEngine timestampEngine;


    public TspServiceImpl(TspRequestValidator tspRequestValidator, SigningProfileService signingProfileService, TspProfileService tspProfileService, TimestampEngine timestampEngine) {
        this.tspRequestValidator = tspRequestValidator;
        this.signingProfileService = signingProfileService;
        this.tspProfileService = tspProfileService;
        this.timestampEngine = timestampEngine;
    }

    public TspResponse processTspRequestForTspProfile(String tspProfileName, TspRequest request) throws NotFoundException, TspException {

        TspProfileDto tspProfile = tspProfileService.getTspProfile(tspProfileName);

        SimplifiedSigningProfileDto defaultSigningProfile = tspProfile.getDefaultSigningProfile();

        return processTspRequestForSigningProfile(defaultSigningProfile.getName(), request);
    }

    public TspResponse processTspRequestForSigningProfile(String signingProfileName, TspRequest request) throws NotFoundException, TspException {
        SigningProfileDto signingProfile = signingProfileService.getSigningProfile(signingProfileName);

        WorkflowDto workflow = signingProfile.getWorkflow();
        if (workflow instanceof TimestampingWorkflowDto timestampingWorkflow) {
            tspRequestValidator.validate(timestampingWorkflow, request);
        } else {
            throw new TspException(TspFailureInfo.BAD_REQUEST, "Can't timestamp with signing profile '%s'".formatted(signingProfileName), "The signing profile '%s' is not configured with timestamping workflow.".formatted(signingProfileName));
        }

        return timestampEngine.process(request, signingProfile);
    }
}

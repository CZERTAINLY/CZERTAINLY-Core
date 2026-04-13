package com.czertainly.core.service.tsa.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.model.client.signing.profile.SimplifiedSigningProfileDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspProfileDto;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.TspProfileService;
import com.czertainly.core.service.tsa.ManagedTimestampEngine;
import com.czertainly.core.service.tsa.TsaService;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.messages.TspResponse;
import com.czertainly.core.service.tsa.validator.TspRequestValidator;
import org.springframework.stereotype.Service;

@Service
public class TsaServiceImpl implements TsaService {

    private final TspRequestValidator tspRequestValidator;
    private final TspProfileService tspProfileService;
    private final SigningProfileService signingProfileService;
    private final ManagedTimestampEngine managedTimestampEngine;


    public TsaServiceImpl(TspRequestValidator tspRequestValidator, SigningProfileService signingProfileService, TspProfileService tspProfileService, ManagedTimestampEngine managedTimestampEngine) {
        this.tspRequestValidator = tspRequestValidator;
        this.signingProfileService = signingProfileService;
        this.tspProfileService = tspProfileService;
        this.managedTimestampEngine = managedTimestampEngine;
    }

    public TspResponse processTspRequestForTspProfile(String tspProfileName, TspRequest request) throws NotFoundException, TspException {

        TspProfileDto tspProfile = tspProfileService.getTspProfile(tspProfileName);

        SimplifiedSigningProfileDto defaultSigningProfile = tspProfile.getDefaultSigningProfile();

        return processTspRequestForSigningProfile(defaultSigningProfile.getName(), request);
    }

    public TspResponse processTspRequestForSigningProfile(String signingProfileName, TspRequest request) throws NotFoundException, TspException {
        var signingProfile = signingProfileService.getManagedTimestampingProfileModel(signingProfileName);

        tspRequestValidator.validate(signingProfile.workflow(), request);

        return managedTimestampEngine.process(request, signingProfile);
    }
}

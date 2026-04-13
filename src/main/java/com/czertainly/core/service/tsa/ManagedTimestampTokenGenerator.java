package com.czertainly.core.service.tsa;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.core.service.tsa.messages.TspRequest;
import org.bouncycastle.tsp.TimeStampToken;

import java.math.BigInteger;
import java.time.Instant;

public interface ManagedTimestampTokenGenerator {

    TimeStampToken generate(TspRequest request, SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ? extends SigningSchemeModel> timestampingProfile,
                            CertificateChain certificateChain, BigInteger serialNumber, Instant genTime
    ) throws TspException;
}

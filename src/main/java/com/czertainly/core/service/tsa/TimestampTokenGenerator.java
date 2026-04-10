package com.czertainly.core.service.tsa;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.model.client.signing.profile.scheme.SigningSchemeDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.core.service.tsa.messages.TspRequest;
import org.bouncycastle.tsp.TimeStampToken;

import java.math.BigInteger;
import java.time.Instant;

public interface TimestampTokenGenerator {

    TimeStampToken generate(TspRequest request, TimestampingWorkflowDto profile, SigningSchemeDto signingScheme,
                            CertificateChain certificateChain, BigInteger serialNumber, Instant genTime
    ) throws TspException;
}

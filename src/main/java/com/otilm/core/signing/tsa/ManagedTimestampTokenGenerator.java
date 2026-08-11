package com.otilm.core.signing.tsa;

import com.otilm.api.interfaces.core.tsp.error.TspException;
import com.otilm.core.model.signing.resolved.ResolvedManagedTimestampingProfile;
import com.otilm.core.signing.tsa.messages.TspRequest;
import java.math.BigInteger;
import java.time.Instant;
import org.bouncycastle.tsp.TimeStampToken;

public interface ManagedTimestampTokenGenerator {

    TimeStampToken generate(TspRequest request, ResolvedManagedTimestampingProfile timestampingProfile,
            CertificateChain certificateChain, BigInteger serialNumber, Instant genTime) throws TspException;
}

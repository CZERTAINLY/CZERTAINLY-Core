package com.czertainly.core.service.tsa;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.signing.profile.scheme.SigningSchemeDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.formatter.SignatureFormatterClient;
import com.czertainly.core.service.tsa.signer.Signer;
import com.czertainly.core.service.tsa.signer.SignerFactory;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampToken;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;

@Component
public class StaticManagedKeyTimestampTokenGenerator implements TimestampTokenGenerator {

    private final SignerFactory signerFactory;

    private final SignatureFormatterClient formatter;

    public StaticManagedKeyTimestampTokenGenerator(SignerFactory signerFactory, SignatureFormatterClient formatter) {
        this.signerFactory = signerFactory;
        this.formatter = formatter;
    }

    @Override
    public TimeStampToken generate(TspRequest request, TimestampingWorkflowDto profile, SigningSchemeDto signingScheme, CertificateChain certificateChain, BigInteger serialNumber, Instant genTime) throws TspException {

        Signer signer = signerFactory.create(signingScheme);
        SignatureAlgorithm signatureAlgorithm = signer.getSignatureAlgorithm();

        byte[] dtbs = formatter.formatDtbs(request, profile, serialNumber, genTime,
                certificateChain, signatureAlgorithm);

        byte[] signature = signer.sign(dtbs);

        byte[] tokenBytes = formatter.formatSigningResponse(request, profile, serialNumber, genTime, certificateChain, dtbs, signature, signatureAlgorithm);

        try {
            return new TimeStampToken(new CMSSignedData(tokenBytes));
        } catch (TSPException | IOException | CMSException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE, "Failed to parse assembled timestamp token", e, "Internal error during token parsing");
        }
    }
}

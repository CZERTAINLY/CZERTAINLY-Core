package com.czertainly.core.service.tsa;


import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.messages.TspResponse;
import com.czertainly.core.service.tsa.certificateprovider.CertificateProviderFactory;
import com.czertainly.core.service.tsa.certificatevalidation.CertificateValidationResult;
import com.czertainly.core.service.tsa.certificatevalidation.SignerCertificateValidationManager;
import com.czertainly.core.service.tsa.clocksource.ClockSource;
import com.czertainly.core.service.tsa.serialnumber.ClockDriftException;
import com.czertainly.core.service.tsa.serialnumber.SerialNumberGenerator;
import com.czertainly.core.service.tsa.timequality.TimeQualityRegister;
import com.czertainly.core.service.tsa.timequality.TimeQualityStatus;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Core engine that processes RFC 3161 timestamp requests.
 *
 * <p>Validates time quality, generates a serial number, delegates token creation
 * to {@link TimestampTokenGenerator}, verifies the signer certificate, and optionally
 * validates the token signature before returning the response.
 */
@Component
public class TimestampEngine {

    private static final Logger logger = LoggerFactory.getLogger(TimestampEngine.class);

    private final TimeQualityRegister timeQualityRegister;
    private final SerialNumberGenerator serialNumberGenerator;
    private final TimestampTokenGenerator tokenGenerator;
    private final SignerCertificateValidationManager certValidationManager;
    private final CertificateProviderFactory certificateProviderFactory;
    private final ClockSource clockSource;


    public TimestampEngine(TimeQualityRegister timeQualityRegister, SerialNumberGenerator serialNumberGenerator, TimestampTokenGenerator tokenGenerator, SignerCertificateValidationManager certValidationManager, CertificateProviderFactory certificateProviderFactory, ClockSource clockSource) {
        this.timeQualityRegister = timeQualityRegister;
        this.serialNumberGenerator = serialNumberGenerator;
        this.tokenGenerator = tokenGenerator;
        this.certValidationManager = certValidationManager;
        this.certificateProviderFactory = certificateProviderFactory;
        this.clockSource = clockSource;
    }

    public TspResponse process(TspRequest request, SigningProfileDto timestampingProfile) throws TspException {

        if (timestampingProfile.getWorkflow() instanceof TimestampingWorkflowDto timestampingWorkflow) {

            var certificateProvider = certificateProviderFactory.getProvider(timestampingProfile.getSigningScheme());

            var timeQualityConfiguration = timestampingWorkflow.getTimeQualityConfiguration();

            if (timeQualityConfiguration != null) {
                var timeStatus = timeQualityRegister.getStatus(timeQualityConfiguration.getName());
                if (timeStatus == TimeQualityStatus.DEGRADED) {
                    logger.warn("Rejecting timestamp request for timestampingProfile '{}' using timeQualityProfile '{}': time quality degraded", timestampingProfile.getName(), timeQualityConfiguration.getName());
                    return TspResponse.rejected(TspFailureInfo.TIME_NOT_AVAILABLE, "Time quality is degraded for timestampingProfile '%s'".formatted(timestampingProfile.getName()));
                } else {
                    logger.info("Time quality status for profile '{}': {}", timeQualityConfiguration.getName(), timeStatus);
                }
            }

            try {
                var serialNumber = serialNumberGenerator.generate();
                var genTime = clockSource.wallTimeInstant();
                var certificateChain = certificateProvider.getCertificateChain(timestampingProfile.getSigningScheme());
                var signerCert = certificateChain.signingCertificate();

                var certValidation = certValidationManager.validate(signerCert, timestampingWorkflow.getQualifiedTimestamp());
                if (certValidation instanceof CertificateValidationResult.Invalid invalid) {
                    logger.error("Signer certificate validation failed: {}", invalid.reason());
                    return TspResponse.rejected(TspFailureInfo.SYSTEM_FAILURE, "Signer certificate validation failed: " + invalid.reason());
                }

                var result = tokenGenerator.generate(request, timestampingWorkflow, timestampingProfile.getSigningScheme(), certificateChain, serialNumber, genTime);

//                if (timestampingWorkflow.getValidateTokenSignature()) {
//                    var verifier = new JcaSimpleSignerInfoVerifierBuilder().build(certificateChain.signingCertificate());
//                    result.validate(verifier);
//                }

                return TspResponse.granted(result.getEncoded());

            }  catch (ClockDriftException e) {
                logger.error("Clock drift detected during timestamp generation", e);
                return TspResponse.rejected(TspFailureInfo.TIME_NOT_AVAILABLE, "Clock drift detected");
            } catch (Exception e) {
                logger.error("Unexpected error during timestamp generation", e);
                return TspResponse.rejected(
                        TspFailureInfo.SYSTEM_FAILURE, "Internal error");
            }
        } else {
            throw new IllegalArgumentException("Signing profile is not configured with timestamping workflow");
        }
    }
}

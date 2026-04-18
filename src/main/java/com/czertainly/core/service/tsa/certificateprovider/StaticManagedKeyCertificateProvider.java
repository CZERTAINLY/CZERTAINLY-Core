package com.czertainly.core.service.tsa.certificateprovider;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.tsa.CertificateChain;
import com.czertainly.core.util.CertificateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

@Component
public class StaticManagedKeyCertificateProvider implements CertificateProvider {

    CertificateService certificateService;

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Override
    public boolean supports(SigningSchemeModel signingScheme) {
        return signingScheme instanceof StaticKeyManagedSigning;
    }

    @Override
    public ValidationResult validate(SigningSchemeModel signingScheme, boolean qualifiedTimestamp) {
        if (!(signingScheme instanceof StaticKeyManagedSigning signingSchemeModel)) {
            return ValidationResult.nok(TspFailureInfo.SYSTEM_FAILURE,
                    "The signing scheme '%s' is not supported by 'StaticManagedKeyCertificateProvider'.".formatted(signingScheme.getClass().getSimpleName()),
                    "The system is misconfigured.");
        }
        if (!CertificateUtil.isCertificateDigitalSigningAcceptable(signingSchemeModel.certificate(), SigningWorkflowType.TIMESTAMPING, qualifiedTimestamp)) {
            return ValidationResult.nok(TspFailureInfo.SYSTEM_FAILURE,
                    "Signer certificate is not acceptable for %s timestamping".formatted(qualifiedTimestamp ? "qualified" : "non-qualified"),
                    "Signer certificate failed validation.");
        }
        return ValidationResult.ok();
    }

    @Override
    public CertificateChain getCertificateChain(SigningSchemeModel signingScheme) throws TspException {
        if (!(signingScheme instanceof StaticKeyManagedSigning signingSchemeModel)) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    String.format("The signing scheme '%s' is not supported by 'StaticManagedKeyCertificateProvider'.", signingScheme.getClass().getSimpleName()),
                    "The system is misconfigured.");
        }

        UUID certificateUUID = signingSchemeModel.certificate().getUuid();
        return fetchCertificateChain(certificateUUID);
    }

    private CertificateChain fetchCertificateChain(UUID certificateUUID) throws TspException {
        List<X509Certificate> chain;
        try {
            chain = certificateService.getCertificateChainForSigning(certificateUUID, true);
        } catch (CertificateException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    String.format("Failed to decode certificate chain for %s. %s", certificateUUID, e.getLocalizedMessage()),
                    "Certificate chain could not be parsed.");
        }
        if (chain.isEmpty()) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    String.format("Signing certificate or its chain is not available for UUID %s.", certificateUUID),
                    "Signing key certificate could not be found.");
        }
        return CertificateChain.of(chain);
    }
}

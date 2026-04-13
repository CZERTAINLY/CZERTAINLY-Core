package com.czertainly.core.service.tsa.certificateprovider;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.core.certificate.CertificateChainResponseDto;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.tsa.CertificateChain;
import com.czertainly.core.util.CertificateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
    public void validate(SigningSchemeModel signingScheme, boolean qualifiedTimestamp) throws TspException {
        if (!(signingScheme instanceof StaticKeyManagedSigning signingSchemeModel)) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    String.format("The signing scheme '%s' is not supported by 'StaticManagedKeyCertificateProvider'.", signingScheme.getClass().getSimpleName()),
                    "The system is misconfigured.");
        }
        if (!CertificateUtil.isCertificateDigitalSigningAcceptable(signingSchemeModel.certificate(), SigningWorkflowType.TIMESTAMPING, qualifiedTimestamp)) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    "Signer certificate is not acceptable for %s timestamping".formatted(qualifiedTimestamp ? "qualified" : "non-qualified"),
                    "Signer certificate failed validation.");
        }
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
        CertificateChainResponseDto certificateChainDto;
        try {
            certificateChainDto = certificateService.getCertificateChain(SecuredUUID.fromUUID(certificateUUID), true);
        } catch (NotFoundException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    String.format("Failed to obtain certificate chain. %s", e.getLocalizedMessage()),
                    "Signing key certificate could not be found.");
        }
        List<X509Certificate> chain = new ArrayList<>();
        for (var dto : certificateChainDto.getCertificates()) {
            chain.add(decodeX509Certificate(dto.getCertificateContent(), dto.getCommonName(), dto.getSerialNumber()));
        }
        return CertificateChain.of(chain);
    }

    private X509Certificate decodeX509Certificate(String base64, String commonName, String serialNumber) throws TspException {
        try {
            return CertificateUtil.getX509Certificate(base64);
        } catch (CertificateException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE,
                    String.format("Failed to decode certificate '%s' (serial: %s) from chain. %s", commonName, serialNumber, e.getLocalizedMessage()),
                    "Certificate chain could not be parsed.");
        }
    }
}

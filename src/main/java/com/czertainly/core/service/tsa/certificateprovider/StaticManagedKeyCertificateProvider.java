package com.czertainly.core.service.tsa.certificateprovider;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.interfaces.core.tsp.error.TspFailureInfo;
import com.czertainly.api.model.client.signing.profile.scheme.SigningSchemeDto;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningDto;
import com.czertainly.api.model.core.certificate.CertificateChainResponseDto;
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
    public CertificateChain getCertificateChain(SigningSchemeDto signingScheme) throws TspException {
        if (signingScheme instanceof StaticKeyManagedSigningDto signingSchemeDto) {
            UUID certificateUUID = signingSchemeDto.getCertificate().getUuid();
                return fetchCertificateChain(certificateUUID);

        } else {
            var className = signingScheme.getClass().getSimpleName();
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE, String.format("The signing scheme '%s' is not supported by 'StaticManagedKeyCertificateProvider'.", className), "The system is misconfigured.");
        }
    }

    @Override
    public boolean supports(SigningSchemeDto signingScheme) {
        return signingScheme instanceof StaticKeyManagedSigningDto;
    }

    private CertificateChain fetchCertificateChain(UUID certificateUUID) throws TspException {
        CertificateChainResponseDto certificateChainDto;
        try {
            certificateChainDto = certificateService.getCertificateChain(SecuredUUID.fromUUID(certificateUUID), true);
        } catch (NotFoundException e) {
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE, String.format("Failed to obtain certificate chain. %s", e.getLocalizedMessage()), "Signing key certificate could not be found.");
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
            throw new TspException(TspFailureInfo.SYSTEM_FAILURE, String.format("Failed to decode certificate '%s' (serial: %s) from chain. %s", commonName, serialNumber, e.getLocalizedMessage()), "Certificate chain could not be parsed.");
        }
    }
}

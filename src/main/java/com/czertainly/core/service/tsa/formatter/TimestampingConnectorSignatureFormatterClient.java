package com.czertainly.core.service.tsa.formatter;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.model.connector.signatures.formatter.ExtensionDto;
import com.czertainly.api.model.common.enums.cryptography.SignatureAlgorithm;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatDtbsRequestDto;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatResponseRequestDto;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.core.service.tsa.formatter.connector.TspSignatureFormatter;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.CertificateChain;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Component
public class TimestampingConnectorSignatureFormatterClient implements SignatureFormatterClient {

    TspSignatureFormatter tspSignatureFormatter;

    @Autowired
    public void setTspSignatureFormatter(TspSignatureFormatter tspSignatureFormatter) {
        this.tspSignatureFormatter = tspSignatureFormatter;
    }

    @Override
    public byte[] formatDtbs(TspRequest request, SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ? extends SigningSchemeModel> timestampingProfile, BigInteger serialNumber,
                             Instant genTime, CertificateChain certificateChain,
                             SignatureAlgorithm signatureAlgorithm) {

        ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel> workflow = timestampingProfile.workflow();

        TimestampingFormatDtbsRequestDto requestDto = new TimestampingFormatDtbsRequestDto();
        requestDto.setData(request.hashedMessage());
        requestDto.setHashAlgorithm(request.hashAlgorithm());
        requestDto.setPolicy(request.policy().orElse(workflow.defaultPolicyId()));
        requestDto.setNonce(request.nonce().orElse(null));
        requestDto.setIncludeSignerCertificate(request.includeSignerCertificate());
        requestDto.setRequestExtensions(toExtensionDtos(request.requestExtensions()));
        requestDto.setSerialNumber(serialNumber);
        requestDto.setSigningTime(genTime);
        requestDto.setAccuracy(workflow.timeQualityConfiguration().getAccuracy().orElse(null));
        requestDto.setSignatureAlgorithm(signatureAlgorithm);
        requestDto.setCertificateChain(encodeBase64DerChain(certificateChain));
        requestDto.setFormatAttributes(workflow.signatureFormatterConnectorAttributes());

        var response = tspSignatureFormatter.formatDtbs(requestDto);
        return response.getDtbs();
    }

    @Override
    public byte[] formatSigningResponse(TspRequest request, SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ? extends SigningSchemeModel> timestampingProfile,
                                        BigInteger serialNumber, Instant genTime, CertificateChain certificateChain,
                                        byte[] dtbs, byte[] signature,
                                        SignatureAlgorithm signatureAlgorithm) throws TspException {

        ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel> workflow = timestampingProfile.workflow();

        TimestampingFormatResponseRequestDto requestDto = new TimestampingFormatResponseRequestDto();
        requestDto.setDtbs(dtbs);
        requestDto.setSignature(signature);
        requestDto.setCertificateChain(encodeBase64DerChain(certificateChain));
        requestDto.setFormatAttributes(workflow.signatureFormatterConnectorAttributes());
        requestDto.setData(request.hashedMessage());
        requestDto.setHashAlgorithm(request.hashAlgorithm());
        requestDto.setPolicy(request.policy().orElse(workflow.defaultPolicyId()));
        requestDto.setNonce(request.nonce().orElse(null));
        requestDto.setIncludeSignerCertificate(request.includeSignerCertificate());
        requestDto.setRequestExtensions(toExtensionDtos(request.requestExtensions()));
        requestDto.setSerialNumber(serialNumber);
        requestDto.setSigningTime(genTime);
        requestDto.setAccuracy(workflow.timeQualityConfiguration().getAccuracy().orElse(null));
        requestDto.setSignatureAlgorithm(signatureAlgorithm);

        var response = tspSignatureFormatter.formatSigningResponse(requestDto);
        return response.getResponse();
    }

    private static List<ExtensionDto> toExtensionDtos(Extensions extensions) {
        if (extensions == null) return Collections.emptyList();
        return Arrays.stream(extensions.getExtensionOIDs())
                .map(oid -> {
                    Extension ext = extensions.getExtension(oid);
                    ExtensionDto dto = new ExtensionDto();
                    dto.setOid(oid.getId());
                    dto.setCritical(ext.isCritical());
                    try {
                        dto.setValue(Base64.getEncoder().encodeToString(ext.getExtnValue().getOctets()));
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to encode extension value for OID " + oid.getId(), e);
                    }
                    return dto;
                })
                .toList();
    }

    private static List<byte[]> encodeBase64DerChain(CertificateChain certificateChain) {
        return certificateChain.chain().stream()
                .map(cert -> {
                    try {
                        return Base64.getEncoder().encode(cert.getEncoded());
                    } catch (CertificateEncodingException e) {
                        throw new IllegalStateException("Failed to encode certificate", e);
                    }
                })
                .toList();
    }
}
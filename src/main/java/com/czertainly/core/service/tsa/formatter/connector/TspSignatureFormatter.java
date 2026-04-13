package com.czertainly.core.service.tsa.formatter.connector;

import com.czertainly.api.interfaces.core.tsp.error.TspException;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.connector.signatures.formatter.ExtensionDto;
import com.czertainly.api.model.connector.signatures.formatter.FormatDtbsRequestDto;
import com.czertainly.api.model.connector.signatures.formatter.FormatDtbsResponseDto;
import com.czertainly.api.model.connector.signatures.formatter.FormatResponseRequestDto;
import com.czertainly.api.model.connector.signatures.formatter.FormattedResponseDto;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatDtbsRequestDto;
import com.czertainly.api.model.connector.signatures.formatter.TimestampingFormatResponseRequestDto;
import com.czertainly.core.service.tsa.messages.TspRequest;
import com.czertainly.core.service.tsa.CertificateChain;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class TspSignatureFormatter {

    static final String ATTR_INCLUDE_TSA_NAME = "includeTsaName";
    static final String ATTR_INCLUDE_CMS_ALGORITHM_PROTECTION = "includeCMSAlgorithmProtection";
    static final String ATTR_INCLUDE_SIGNING_TIME = "includeSigningTimeAttribute";
    static final String ATTR_QUALIFIED_TIMESTAMP = "isQualifiedTimestamp";

    public FormatDtbsResponseDto formatDtbs(FormatDtbsRequestDto request) {
        var tsRequest = (TimestampingFormatDtbsRequestDto) request;
        var attrs = tsRequest.getFormatAttributes();
        var certificateChain = decodeCertificateChain(tsRequest.getCertificateChain());

        var tspRequest = buildTspRequest(tsRequest);

        try {
            byte[] dtbs = TimestampTokenAssembler.formatDtbs(
                    tsRequest.getSignatureAlgorithm(),
                    certificateChain,
                    tsRequest.getPolicy(),
                    tsRequest.getSerialNumber(),
                    Date.from(tsRequest.getSigningTime()),
                    tsRequest.getAccuracy(),
                    tsRequest.isIncludeSignerCertificate(),
                    getBooleanAttribute(attrs, ATTR_INCLUDE_TSA_NAME),
                    getBooleanAttribute(attrs, ATTR_INCLUDE_CMS_ALGORITHM_PROTECTION),
                    getBooleanAttribute(attrs, ATTR_INCLUDE_SIGNING_TIME),
                    getBooleanAttribute(attrs, ATTR_QUALIFIED_TIMESTAMP),
                    tspRequest
            );
            return new FormatDtbsResponseDto(dtbs);
        } catch (TspException e) {
            throw new RuntimeException(e);
        }
    }

    public FormattedResponseDto formatSigningResponse(FormatResponseRequestDto request) {
        var tsRequest = (TimestampingFormatResponseRequestDto) request;
        var attrs = tsRequest.getFormatAttributes();
        var certificateChain = decodeCertificateChain(tsRequest.getCertificateChain());

        var tspRequest = new TspRequest(
                tsRequest.getHashAlgorithm(),
                tsRequest.getData(),
                Optional.ofNullable(tsRequest.getPolicy()),
                Optional.ofNullable(tsRequest.getNonce()),
                tsRequest.isIncludeSignerCertificate(),
                toExtensions(tsRequest.getRequestExtensions())
        );

        try {
            var token = TimestampTokenAssembler.formatSigningResponse(
                    tsRequest.getSignatureAlgorithm(),
                    certificateChain,
                    tsRequest.getPolicy(),
                    tsRequest.getSerialNumber(),
                    Date.from(tsRequest.getSigningTime()),
                    tsRequest.getAccuracy(),
                    tsRequest.isIncludeSignerCertificate(),
                    getBooleanAttribute(attrs, ATTR_INCLUDE_TSA_NAME),
                    getBooleanAttribute(attrs, ATTR_INCLUDE_CMS_ALGORITHM_PROTECTION),
                    getBooleanAttribute(attrs, ATTR_INCLUDE_SIGNING_TIME),
                    getBooleanAttribute(attrs, ATTR_QUALIFIED_TIMESTAMP),
                    tspRequest,
                    tsRequest.getSignature()
            );
            return new FormattedResponseDto(token.getEncoded());
        } catch (TspException | java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static TspRequest buildTspRequest(TimestampingFormatDtbsRequestDto tsRequest) {
        return new TspRequest(
                tsRequest.getHashAlgorithm(),
                tsRequest.getData(),
                Optional.ofNullable(tsRequest.getPolicy()),
                Optional.ofNullable(tsRequest.getNonce()),
                tsRequest.isIncludeSignerCertificate(),
                toExtensions(tsRequest.getRequestExtensions())
        );
    }

    private static Extensions toExtensions(List<ExtensionDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return null;
        var gen = new ExtensionsGenerator();
        for (var dto : dtos) {
            gen.addExtension(new Extension(
                    new ASN1ObjectIdentifier(dto.getOid()),
                    dto.isCritical(),
                    new DEROctetString(Base64.getDecoder().decode(dto.getValue()))
            ));
        }
        return gen.generate();
    }

    private static boolean getBooleanAttribute(List<RequestAttribute> attrs, String name) {
        Boolean value = AttributeDefinitionUtils.getAttributeContent(name, attrs, true);
        return Boolean.TRUE.equals(value);
    }

    private static CertificateChain decodeCertificateChain(List<byte[]> encoded) {
        try {
            var cf = CertificateFactory.getInstance("X.509");
            var certs = encoded.stream()
                    .map(bytes -> {
                        try {
                            var plainBytes = Base64.getDecoder().decode(bytes);
                            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(plainBytes));
                        } catch (Exception e) {
                            throw new IllegalArgumentException("Failed to decode certificate", e);
                        }
                    })
                    .toList();
            return CertificateChain.of(certs);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode certificate chain", e);
        }
    }
}
package com.czertainly.core.mapper.signing;

import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.core.dao.entity.signing.DigitalSignature;

import java.time.ZonedDateTime;

public class DigitalSignatureMapper {

    private DigitalSignatureMapper() {
    }

    public static DigitalSignatureDto toDto(DigitalSignature signature) {
        DigitalSignatureDto dto = new DigitalSignatureDto();
        dto.setUuid(signature.getUuid().toString());
        dto.setName(signature.getName());
        ZonedDateTime signingTimeZoned = signature.getSigningTime() != null ? signature.getSigningTime().toZonedDateTime() : null;
        dto.setSigningTime(signingTimeZoned);
        ZonedDateTime createdAtZoned = signature.getCreatedAt() != null ? signature.getCreatedAt().toZonedDateTime() : null;
        dto.setCreatedAt(createdAtZoned);
        dto.setSignatureValue(signature.getSignatureValue());
        return dto;
    }

    public static DigitalSignatureListDto toListDto(DigitalSignature signature) {
        DigitalSignatureListDto dto = new DigitalSignatureListDto();
        dto.setUuid(signature.getUuid().toString());
        dto.setName(signature.getName());
        ZonedDateTime signingTimeZoned = signature.getSigningTime() != null ? signature.getSigningTime().toZonedDateTime() : null;
        dto.setSigningTime(signingTimeZoned);
        ZonedDateTime createdAtZoned = signature.getCreatedAt() != null ? signature.getCreatedAt().toZonedDateTime() : null;
        dto.setCreatedAt(createdAtZoned);
        return dto;
    }
}

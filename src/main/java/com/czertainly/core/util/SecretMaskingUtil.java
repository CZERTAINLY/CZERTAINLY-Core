package com.czertainly.core.util;

import com.czertainly.api.model.common.attribute.AttributeType;
import com.czertainly.api.model.common.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SecretMaskingUtil {

    private static final List<AttributeType> TO_BE_MASKED = List.of(AttributeType.SECRET);

    public static List<ResponseAttributeDto> maskSecret(List<ResponseAttributeDto> responseAttributeDtos) {
        List<ResponseAttributeDto> responses = new ArrayList<>();
        for (ResponseAttributeDto responseAttributeDto : responseAttributeDtos) {
            if (responseAttributeDto.getType() == null) {
                //Do nothing
            } else if (TO_BE_MASKED.contains(responseAttributeDto.getType())) {
                responseAttributeDto.setContent(new BaseAttributeContent<String>(null));
            } else if (responseAttributeDto.getType().equals(AttributeType.CREDENTIAL)) {
                ObjectMapper OBJECT_MAPPER = new ObjectMapper();
                CredentialDto credentialDto = OBJECT_MAPPER.convertValue(((LinkedHashMap) responseAttributeDto.getContent()).get("data"), CredentialDto.class);
                List<ResponseAttributeDto> credentialAttrs = new ArrayList<>();
                if (credentialDto.getAttributes() == null) {
                    responseAttributeDto.setContent(null);
                } else {
                    for (ResponseAttributeDto credentialAttributeDto : credentialDto.getAttributes()) {
                        if (TO_BE_MASKED.contains(credentialAttributeDto.getType())) {
                            credentialAttributeDto.setContent(new BaseAttributeContent<String>(null));
                        }
                        credentialAttrs.add(credentialAttributeDto);
                    }
                    responseAttributeDto.setContent(new JsonAttributeContent(credentialDto.getName(), credentialDto));
                }
            }
            responses.add(responseAttributeDto);
        }
        return responseAttributeDtos;
    }
}

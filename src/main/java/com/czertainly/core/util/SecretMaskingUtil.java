package com.czertainly.core.util;

import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SecretMaskingUtil {

    private static final List<AttributeContentType> TO_BE_MASKED = List.of(AttributeContentType.SECRET);

    public static List<ResponseAttributeDto> maskSecret(List<ResponseAttributeDto> responseAttributeDtos) {
        List<ResponseAttributeDto> responses = new ArrayList<>();
        for (ResponseAttributeDto responseAttributeDto : responseAttributeDtos) {
            if (responseAttributeDto.getType() == null) {
                //Do nothing
            } else if (TO_BE_MASKED.contains(responseAttributeDto.getType())) {
                responseAttributeDto.setContent(List.of(new StringAttributeContent(null)));
            } else if (responseAttributeDto.getType().equals(AttributeContentType.CREDENTIAL)) {
                ObjectMapper OBJECT_MAPPER = new ObjectMapper();
                CredentialDto credentialDto = OBJECT_MAPPER.convertValue(((LinkedHashMap) responseAttributeDto.getContent()).get("data"), CredentialDto.class);
                List<ResponseAttributeDto> credentialAttrs = new ArrayList<>();
                if (credentialDto.getAttributes() == null) {
                    responseAttributeDto.setContent(null);
                } else {
                    for (ResponseAttributeDto credentialAttributeDto : credentialDto.getAttributes()) {
                        if (TO_BE_MASKED.contains(credentialAttributeDto.getType())) {
                            credentialAttributeDto.setContent(List.of(new StringAttributeContent(null)));
                        }
                        credentialAttrs.add(credentialAttributeDto);
                    }
                    responseAttributeDto.setContent(List.of(new ObjectAttributeContent(credentialDto.getName(), credentialDto)));
                }
            }
            responses.add(responseAttributeDto);
        }
        return responseAttributeDtos;
    }
}

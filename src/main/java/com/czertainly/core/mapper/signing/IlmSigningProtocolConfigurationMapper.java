package com.czertainly.core.mapper.signing;

import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationListDto;
import com.czertainly.core.dao.entity.signing.IlmSigningProtocolConfiguration;

import java.util.List;

public class IlmSigningProtocolConfigurationMapper {

    private IlmSigningProtocolConfigurationMapper() {
    }

    public static IlmSigningProtocolConfigurationDto toDto(IlmSigningProtocolConfiguration configuration, List<ResponseAttribute> customAttributes) {
        IlmSigningProtocolConfigurationDto dto = new IlmSigningProtocolConfigurationDto();
        dto.setUuid(configuration.getUuid().toString());
        dto.setName(configuration.getName());
        dto.setDescription(configuration.getDescription());
        dto.setCustomAttributes(customAttributes);
        return dto;
    }

    public static IlmSigningProtocolConfigurationListDto toListDto(IlmSigningProtocolConfiguration configuration) {
        IlmSigningProtocolConfigurationListDto dto = new IlmSigningProtocolConfigurationListDto();
        dto.setUuid(configuration.getUuid().toString());
        dto.setName(configuration.getName());
        return dto;
    }
}

package com.czertainly.core.mapper.signing;

import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.profile.SimplifiedSigningProfileDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationListDto;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TspConfiguration;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

public class TspConfigurationMapper {

    private TspConfigurationMapper() {
    }

    public static TspConfigurationDto toDto(TspConfiguration configuration, List<ResponseAttribute> customAttributes) {
        TspConfigurationDto dto = new TspConfigurationDto();
        dto.setUuid(configuration.getUuid().toString());
        dto.setName(configuration.getName());
        dto.setDescription(configuration.getDescription());
        dto.setEnabled(configuration.getEnabled() != null ? configuration.getEnabled() : false);
        if (configuration.getDefaultSigningProfile() != null) {
            SimplifiedSigningProfileDto signingProfileDto = SigningProfileMapper.toSimpleDto(configuration.getDefaultSigningProfile());
            dto.setSigningUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + "/v1/protocols/tsp/" + configuration.getName() + "/sign");
            dto.setDefaultSigningProfile(signingProfileDto);
        }
        dto.setCustomAttributes(customAttributes);
        return dto;
    }

    public static TspConfigurationListDto toListDto(TspConfiguration configuration) {
        TspConfigurationListDto dto = new TspConfigurationListDto();
        dto.setUuid(configuration.getUuid().toString());
        dto.setName(configuration.getName());
        dto.setDescription(configuration.getDescription());
        dto.setEnabled(configuration.getEnabled() != null ? configuration.getEnabled() : false);
        if (configuration.getDefaultSigningProfile() != null) {
            dto.setDefaultSigningProfile(SigningProfileMapper.toSimpleDto(configuration.getDefaultSigningProfile()));
        }
        return dto;
    }
}

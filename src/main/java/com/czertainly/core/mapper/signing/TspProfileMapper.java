package com.czertainly.core.mapper.signing;

import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.profile.SimplifiedSigningProfileDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspProfileDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspProfileListDto;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TspProfile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

public class TspProfileMapper {

    private TspProfileMapper() {
    }

    public static TspProfileDto toDto(TspProfile profile, List<ResponseAttribute> customAttributes) {
        TspProfileDto dto = new TspProfileDto();
        dto.setUuid(profile.getUuid().toString());
        dto.setName(profile.getName());
        dto.setDescription(profile.getDescription());
        dto.setEnabled(profile.getEnabled() != null ? profile.getEnabled() : false);
        if (profile.getDefaultSigningProfile() != null) {
            SimplifiedSigningProfileDto signingProfileDto = SigningProfileMapper.toSimpleDto(profile.getDefaultSigningProfile());
            dto.setSigningUrl(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                    + "/v1/protocols/tsp/" + profile.getName() + "/sign");
            dto.setDefaultSigningProfile(signingProfileDto);
        }
        dto.setCustomAttributes(customAttributes);
        return dto;
    }

    public static TspProfileListDto toListDto(TspProfile profile) {
        TspProfileListDto dto = new TspProfileListDto();
        dto.setUuid(profile.getUuid().toString());
        dto.setName(profile.getName());
        dto.setDescription(profile.getDescription());
        dto.setEnabled(profile.getEnabled() != null ? profile.getEnabled() : false);
        if (profile.getDefaultSigningProfile() != null) {
            dto.setDefaultSigningProfile(SigningProfileMapper.toSimpleDto(profile.getDefaultSigningProfile()));
        }
        return dto;
    }
}

package com.czertainly.core.dao.entity.oid;

import com.czertainly.api.model.core.oid.RdnAttributeTypeOidPropertiesDto;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@DiscriminatorValue("RDN_ATTRIBUTE_TYPE")
public class RdnAttributeTypeCustomOidEntry extends CustomOidEntry {

    @Column(name = "code")
    private String code;

    @Column(name = "altCodes")
    private List<String> altCodes = new ArrayList<>();


    public RdnAttributeTypeOidPropertiesDto mapToPropertiesDto() {
        RdnAttributeTypeOidPropertiesDto propertiesDto = new RdnAttributeTypeOidPropertiesDto();
        propertiesDto.setCode(code);
        propertiesDto.setAltCodes(altCodes);
        return propertiesDto;
    }
}

package com.czertainly.core.dao.entity.oid;

import com.czertainly.api.model.core.oid.OidCategory;
import com.czertainly.api.model.core.oid.OidEntryDetailResponseDto;
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
@DiscriminatorValue(OidCategory.Codes.RDN_ATTRIBUTE_TYPE)
public class RdnAttributeTypeOidEntry extends OidEntry {

    @Column(name = "valueType")
    private String valueType;

    @Column(name = "code")
    private String code;

    @Column(name = "altCodes")
    private List<String> altCodes = new ArrayList<>();


    public RdnAttributeTypeOidPropertiesDto mapToPropertiesDto() {
        RdnAttributeTypeOidPropertiesDto propertiesDto = new RdnAttributeTypeOidPropertiesDto();
        propertiesDto.setCode(code);
        propertiesDto.setValueType(valueType);
        propertiesDto.setAltCodes(altCodes);
        return propertiesDto;
    }
}

package com.czertainly.core.util;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.core.attribute.CsrAttributes;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class CertificateRequestUtilsTest {

    @Test
    void testBuildSubject() {
        List<RequestAttributeDto> attributes = new ArrayList<>();
        attributes.add(createRequestAttributeDto(CsrAttributes.COMMON_NAME_ATTRIBUTE_NAME, "+,\\\"><;123ěščřžýáíé"));
        attributes.add(createRequestAttributeDto(CsrAttributes.ORGANIZATION_UNIT_ATTRIBUTE_NAME, " ou"));
        attributes.add(createRequestAttributeDto(CsrAttributes.ORGANIZATION_ATTRIBUTE_NAME, "#o"));
        attributes.add(createRequestAttributeDto(CsrAttributes.LOCALITY_ATTRIBUTE_NAME, "locality "));
        attributes.add(createRequestAttributeDto(CsrAttributes.STATE_ATTRIBUTE_NAME, "state, C=country"));
        X500Principal x500Principal = CertificateRequestUtils.buildSubject(attributes);
        Assertions.assertEquals("CN=\\+\\,\\\\\\\"\\>\\<\\;123ěščřžýáíé,OU=\\ ou,O=\\#o,L=locality\\ ,ST=state\\, C\\=country", x500Principal.getName());
        X500Name x500Name = new X500Name(x500Principal.getName());
        Assertions.assertEquals(5, x500Name.getRDNs().length);
    }

    private RequestAttributeDto createRequestAttributeDto(String name, String data) {
        RequestAttributeDto attributeDto = new RequestAttributeDto();
        attributeDto.setUuid(UUID.randomUUID().toString());
        attributeDto.setName(name);
        attributeDto.setContentType(AttributeContentType.STRING);
        List<BaseAttributeContent> content = new ArrayList<>();
        BaseAttributeContent attributeContent = new StringAttributeContent(data);
        content.add(attributeContent);
        attributeDto.setContent(content);
        return attributeDto;
    }
}

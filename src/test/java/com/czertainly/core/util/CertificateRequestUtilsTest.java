package com.czertainly.core.util;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.RequestAttributeV3Dto;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
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
        List<RequestAttribute> attributes = new ArrayList<>();
        attributes.add(createRequestAttribute(CsrAttributes.COMMON_NAME_ATTRIBUTE_NAME, "+,\\\"><;123ěščřžýáíé"));
        attributes.add(createRequestAttribute(CsrAttributes.ORGANIZATION_UNIT_ATTRIBUTE_NAME, " ou"));
        attributes.add(createRequestAttribute(CsrAttributes.ORGANIZATION_ATTRIBUTE_NAME, "#o"));
        attributes.add(createRequestAttribute(CsrAttributes.LOCALITY_ATTRIBUTE_NAME, "locality "));
        attributes.add(createRequestAttribute(CsrAttributes.STATE_ATTRIBUTE_NAME, "state, C=country"));
        X500Principal x500Principal = CertificateRequestUtils.buildSubject(attributes);
        Assertions.assertEquals("CN=\\+\\,\\\\\\\"\\>\\<\\;123ěščřžýáíé,OU=\\ ou,O=\\#o,L=locality\\ ,ST=state\\, C\\=country", x500Principal.getName());
        X500Name x500Name = new X500Name(x500Principal.getName());
        Assertions.assertEquals(5, x500Name.getRDNs().length);
    }

    private RequestAttribute createRequestAttribute(String name, String data) {
        RequestAttributeV3Dto attributeDto = new RequestAttributeV3Dto();
        attributeDto.setUuid(UUID.randomUUID());
        attributeDto.setName(name);
        attributeDto.setContentType(AttributeContentType.STRING);
        attributeDto.setContent(List.of(new StringAttributeContentV3(data)));
        return attributeDto;
    }
}

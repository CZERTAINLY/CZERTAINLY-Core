package com.czertainly.core.dao.entity;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import jakarta.persistence.*;
import lombok.Data;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@Data
@Entity
@Table(name = "certificate_request")
public class CertificateRequest extends UniquelyIdentifiedAndAudited  {

    @Column(name = "certificate_type")
    @Enumerated(EnumType.STRING)
    private CertificateType certificateType;

    @Column(name = "certificate_request_format")
    @Enumerated(EnumType.STRING)
    private CertificateRequestFormat certificateRequestFormat;

    @Column(name = "public_key_algorithm")
    private String publicKeyAlgorithm;

    @Column(name = "signature_algorithm")
    private String signatureAlgorithm;

    @Column(name = "fingerprint")
    private String fingerprint;

    @Column(name = "content", length = Integer.MAX_VALUE)
    private String content;

    @Column(name = "common_name")
    private String commonName;

    @Column(name = "subject_dn")
    private String subjectDn;

    @Column(name = "attributes", length = Integer.MAX_VALUE)
    private String attributes;

    @Column(name = "signature_attributes", length = Integer.MAX_VALUE)
    private String signatureAttributes;

    @Column(name = "subject_alternative_names")
    private String subjectAlternativeNames;

    @Column(name = "key_usage")
    private String keyUsage;

    public void setContent(String content) throws NoSuchAlgorithmException {
        this.content = content;
        final byte[] contentDecoded = Base64.getDecoder().decode(content);
        this.fingerprint = CertificateUtil.getThumbprint(contentDecoded);
    }

    public List<DataAttribute> getAttributes() {
        return AttributeDefinitionUtils.deserialize(attributes, DataAttribute.class);
    }

    public void setAttributes(List<DataAttribute> csrAttributes) {
        this.attributes = AttributeDefinitionUtils.serialize(csrAttributes);
    }

    public List<RequestAttributeDto> getSignatureAttributes() {
        return AttributeDefinitionUtils.deserializeRequestAttributes(signatureAttributes);
    }

    public void setSignatureAttributes(String signatureAttributes) {
        this.signatureAttributes = signatureAttributes;
    }

    public void setSignatureAttributes(List<RequestAttributeDto> signatureAttributes) {
        this.signatureAttributes = AttributeDefinitionUtils.serializeRequestAttributes(signatureAttributes);
    }
}

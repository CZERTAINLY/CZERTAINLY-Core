package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.core.util.CertificateUtil;
import jakarta.persistence.*;
import lombok.Data;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Data
@Entity
@Table(name = "certificate_request")
public class CertificateRequestEntity extends UniquelyIdentifiedAndAudited  {

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

    @Column(name = "subject_alternative_names")
    private String subjectAlternativeNames;

    @Column(name = "key_usage")
    private String keyUsage;

    public void setContent(String content) throws NoSuchAlgorithmException {
        this.content = content;
        if (this.fingerprint == null) {
            final byte[] contentDecoded = Base64.getDecoder().decode(content);
            this.fingerprint = CertificateUtil.getThumbprint(contentDecoded);
        }
    }

    public byte[] getContentDecoded() {
        return Base64.getDecoder().decode(content);
    }

}

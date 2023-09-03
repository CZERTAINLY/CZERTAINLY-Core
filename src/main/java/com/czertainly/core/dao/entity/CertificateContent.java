package com.czertainly.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "certificate_content")
public class CertificateContent {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "certificate_content_seq")
    @SequenceGenerator(name = "certificate_content_seq", sequenceName = "certificate_content_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "fingerprint")
    private String fingerprint;

    @Column(name = "content", length = Integer.MAX_VALUE)
    private String content;

    @OneToMany(mappedBy = "certificateContent", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<DiscoveryCertificate> discoveryCertificates = new HashSet<>();

    @OneToOne(mappedBy = "certificateContent", fetch = FetchType.LAZY)
    @JsonIgnore
    private Certificate certificate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Set<DiscoveryCertificate> getDiscoveryCertificates() {
        return discoveryCertificates;
    }

    public void setDiscoveryCertificates(Set<DiscoveryCertificate> discoveryCertificates) {
        this.discoveryCertificates = discoveryCertificates;
    }

	public Certificate getCertificate() {
		return certificate;
	}

	public void setCertificate(Certificate certificate) {
		this.certificate = certificate;
	}

	@Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("fingerprint", fingerprint)
                .append("content", content)
                .toString();
    }
}

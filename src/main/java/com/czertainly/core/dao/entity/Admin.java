package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.admin.AdminDto;
import com.czertainly.api.model.core.admin.AdminRole;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "admin")
public class Admin extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<AdminDto> {

    @Column(name = "username")
    private String username;

    @Column(name = "name")
    private String name;

    @Column(name = "surname")
    private String surname;

    @Column(name = "description")
    private String description;

    @Column(name = "email")
    private String email;

    @OneToOne
	@JoinColumn(name = "certificate_uuid", nullable = false, insertable = false, updatable = false)
	private Certificate certificate;

    @Column(name = "certificate_uuid", nullable = false)
    private String certificateUuid;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private AdminRole role;

    @Column(name = "enabled")
    private Boolean enabled;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("uuid", uuid)
                .append("username", username)
                .append("name", name)
                .append("email", email)
                .append("surname", surname)
                .toString();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public AdminRole getRole() {
        return role;
    }

    public void setRole(AdminRole role) {
        this.role = role;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Certificate getCertificate() {
		return certificate;
	}

	public void setCertificate(Certificate certificate) {
		this.certificate = certificate;
        this.certificateUuid = certificate.getUuid();
    }

    public String getCertificateUuid() {
        return certificateUuid;
    }

    public void setCertificateUuid(String certificateUuid) {
        this.certificateUuid = certificateUuid;
    }

    @Override
    public AdminDto mapToDto() {
        AdminDto dto = new AdminDto();
        dto.setUuid(uuid);
        dto.setUsername(username);
        dto.setName(name);
        dto.setDescription(description);
        dto.setEnabled(enabled);
        dto.setRole(role);
        dto.setEmail(email);
        dto.setSurname(surname);
        dto.setSerialNumber(serialNumber);
        if (certificate != null) {
			dto.setCertificate(certificate.mapToDto());
		}
        return dto;
    }
}

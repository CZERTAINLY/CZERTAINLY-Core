package com.czertainly.core.dao.entity;

import com.czertainly.api.model.core.client.ClientDto;
import com.czertainly.core.util.DtoMapper;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "client")
public class Client extends UniquelyIdentifiedAndAudited implements Serializable, DtoMapper<ClientDto> {

	@Column(name = "name")
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "serial_number")
	private String serialNumber;

	@OneToOne
	@JoinColumn(name = "certificate_uuid", nullable = false)
	private Certificate certificate;

	@ManyToMany(mappedBy = "clients")
	private Set<RaProfile> raProfiles = new HashSet<>();

	@Column(name = "enabled")
	private Boolean enabled;

	@Override
	public ClientDto mapToDto() {
		ClientDto dto = new ClientDto();
		dto.setUuid(uuid);
		dto.setName(name);
		dto.setDescription(description);
		dto.setEnabled(enabled);
		dto.setSerialNumber(serialNumber);
        if (certificate != null) {
            dto.setCertificate(certificate.mapToDto());
        }
        return dto;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Certificate getCertificate() {
		return certificate;
	}

	public void setCertificate(Certificate certificate) {
		this.certificate = certificate;
	}

	public Set<RaProfile> getRaProfiles() {
		return raProfiles;
	}

	public void setRaProfiles(Set<RaProfile> raProfiles) {
		this.raProfiles = raProfiles;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("name", name)
				.append("enabled", enabled).append("certificate", certificate).append("serialNumber", serialNumber)
				.append("uuid", uuid).toString();
	}
}

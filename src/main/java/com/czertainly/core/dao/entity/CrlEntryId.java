package com.czertainly.core.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Embedded class for a composite primary key
 */
@Getter
@Setter
@Embeddable
public class CrlEntryId implements Serializable {

    @Column(name = "crl_uuid", nullable = false)
    private UUID crlUuid;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    public CrlEntryId() {
    }

    public CrlEntryId(UUID crlUuid, String serialNumber) {
        this.crlUuid = crlUuid;
        this.serialNumber = serialNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass())
            return false;

        CrlEntryId that = (CrlEntryId) o;
        return Objects.equals(crlUuid, that.crlUuid) &&
                Objects.equals(serialNumber, that.serialNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(crlUuid, serialNumber);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("crlUuid", crlUuid)
                .append("serialNumber", serialNumber)
                .toString();
    }
}
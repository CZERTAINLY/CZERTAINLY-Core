package com.czertainly.core.dao.entity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.czertainly.api.model.core.cbom.CbomDto;
import com.czertainly.core.util.DtoMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.proxy.HibernateProxy;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "cbom")
public class Cbom extends UniquelyIdentified implements DtoMapper<CbomDto> {

    @Id
    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "source", nullable = true)
    private String source;

    @Column(name = "algorithms_count", nullable = false)
    private int algorithmsCount;

    @Column(name = "certificates_count", nullable = false)
    private int certificatesCount;

    @Column(name = "protocols_count", nullable = false)
    private int protocolsCount;

    @Column(name = "crypto_material_count", nullable = false)
    private int cryptoMaterialCount;

    @Column(name = "total_assets_count", nullable = false)
    private int totalAssetsCount;

    @Override
    public CbomDto mapToDto() {
        CbomDto dto = new CbomDto();
        OffsetDateTime timestamp = this.createdAt.atOffset(ZoneOffset.UTC);

        dto.setSerialNumber(this.serialNumber);
        dto.setVersion(String.valueOf(this.version));
        dto.setTimestamp(timestamp);
        dto.setSource(this.source);
        dto.setAlgorithms(this.algorithmsCount);
        dto.setCertificates(this.certificatesCount);
        dto.setProtocols(this.protocolsCount);
        dto.setCryptoMaterial(this.cryptoMaterialCount);
        dto.setTotalAssets(this.totalAssetsCount);
        return dto;
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}

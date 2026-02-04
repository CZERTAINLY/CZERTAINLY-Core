package com.czertainly.core.dao.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.Cbom;

@Repository
public interface CbomRepository extends SecurityFilterRepository<Cbom, UUID> {

    @Query(value = """
        SELECT
            uuid,
            serial_number,
            version,
            created_at,
            source,
            algorithms_count,
            certificates_count,
            protocols_count,
            crypto_material_count,
            total_assets_count
        FROM cbom c
        WHERE c.serial_number = :serial_number
    """
    )
    List<Cbom> findBySerialNumber(@Param("serial_number") String serialNumber);
}

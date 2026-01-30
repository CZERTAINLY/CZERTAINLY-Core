package com.czertainly.core.dao.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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
        WHERE c.serial_number = :serial_number AND c.version = version
    """
    )
    Optional<Cbom> findBySerialNumberVersion(@Param("serial_number") String serial_number, @Param("version") int version);
}

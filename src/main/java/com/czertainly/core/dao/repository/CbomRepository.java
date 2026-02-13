package com.czertainly.core.dao.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.Cbom;

@Repository
public interface CbomRepository extends SecurityFilterRepository<Cbom, UUID> {

    List<Cbom> findBySerialNumber(@Param("serial_number") String serialNumber);
}

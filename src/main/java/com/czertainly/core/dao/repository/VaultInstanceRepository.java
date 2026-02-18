package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.VaultInstance;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VaultInstanceRepository extends SecurityFilterRepository<VaultInstance, UUID> {

    boolean existsByName(String name);
}

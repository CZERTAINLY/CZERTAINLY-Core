package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.VaultInstance;
import org.springframework.stereotype.Repository;

@Repository
public interface VaultInstanceRepository extends SecurityFilterRepository<VaultInstance, Long> {

    boolean existsByName(String name);
}

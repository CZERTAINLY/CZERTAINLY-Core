package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.VaultProfile;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VaultProfileRepository extends SecurityFilterRepository<VaultProfile, UUID> {

    Boolean existsByName(String name);

    @Query("SELECT vp.name FROM VaultProfile vp")
    List<String> findAllNames();
}

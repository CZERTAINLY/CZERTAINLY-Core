package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.VaultProfile;

import java.util.UUID;

public interface VaultProfileRepository extends SecurityFilterRepository<VaultProfile, UUID> {

    Boolean existsByName(String name);
}

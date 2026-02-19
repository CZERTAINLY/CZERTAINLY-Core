package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Secret;

import java.util.UUID;

public interface SecretRepository extends SecurityFilterRepository<Secret, UUID> {

    Boolean existsByName(String name);
}

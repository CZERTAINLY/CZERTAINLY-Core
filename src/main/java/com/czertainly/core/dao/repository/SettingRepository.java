package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.dao.entity.Setting;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettingRepository extends SecurityFilterRepository<Setting, UUID> {

    Optional<Setting> findByUuid(UUID uuid);

    List<Setting> findBySection(SettingsSection section);

}

package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.core.settings.SettingsSectionCategory;
import com.czertainly.core.dao.entity.Setting;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettingRepository extends SecurityFilterRepository<Setting, UUID> {

    Optional<Setting> findByUuid(UUID uuid);

    List<Setting> findBySection(SettingsSection section);

    List<Setting> findBySectionAndCategory(SettingsSection section, String category);

    Setting findBySectionAndCategoryAndName(SettingsSection section, String category, String name);

    long deleteBySectionAndCategory(SettingsSection section, String category);

    long deleteBySectionAndCategoryAndName(SettingsSection section, String category, String name);
}

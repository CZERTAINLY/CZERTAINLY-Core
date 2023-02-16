package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.setting.Section;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.Setting;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface SettingRepository extends SecurityFilterRepository<Setting, UUID> {

    Optional<Setting> findByName(String name);

    Optional<Setting> findByUuid(UUID uuid);

    Optional<Setting> findBySection(Section section);
}

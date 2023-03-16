package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.settings.Section;
import com.czertainly.core.dao.entity.Setting;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface SettingRepository extends SecurityFilterRepository<Setting, UUID> {

    Optional<Setting> findByUuid(UUID uuid);

    List<Setting> findBySection(Section section);

    Optional<Setting> findBySectionAndName(Section section, String name);
}

package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.DiscoveryHistory;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public interface DiscoveryRepository extends SecurityFilterRepository<DiscoveryHistory, Long> {

    Optional<DiscoveryHistory> findByUuid(UUID uuid);

	Optional<DiscoveryHistory> findByName(String name);
}

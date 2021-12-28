package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.DiscoveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface DiscoveryRepository extends JpaRepository<DiscoveryHistory, Long> {

    Optional<DiscoveryHistory> findByUuid(String uuid);

	Optional<DiscoveryHistory> findByName(String name);
}

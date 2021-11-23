package com.czertainly.core.dao.repository;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.DiscoveryHistory;

@Repository
@Transactional
public interface DiscoveryRepository extends JpaRepository<DiscoveryHistory, Long> {

    Optional<DiscoveryHistory> findByUuid(String uuid);

	Optional<DiscoveryHistory> findByName(String name);
}

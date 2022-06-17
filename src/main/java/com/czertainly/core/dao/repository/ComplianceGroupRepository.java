package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface ComplianceGroupRepository extends JpaRepository<ComplianceGroup, Long> {

    Optional<ComplianceGroup> findByUuid(String uuid);

    Optional<ComplianceGroup> findByName(String name);

    List<ComplianceGroup> findByConnector(Connector connector);

    List<ComplianceGroup> findByKind(String kind);

    List<ComplianceGroup> findByConnectorAndKind(Connector connector, String kind);

    Optional<ComplianceGroup> findByUuidAndConnectorAndKind(String uuid, Connector connector, String kind);
}

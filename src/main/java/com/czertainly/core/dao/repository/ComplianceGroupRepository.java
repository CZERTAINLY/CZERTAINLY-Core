package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.ComplianceGroup;
import com.czertainly.core.dao.entity.Connector;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceGroupRepository extends SecurityFilterRepository<ComplianceGroup, Long> {

    Optional<ComplianceGroup> findByUuid(UUID uuid);

    Optional<ComplianceGroup> findByName(String name);

    List<ComplianceGroup> findByConnectorAndKind(Connector connector, String kind);

    List<ComplianceGroup> findByConnectorUuidAndKind(UUID connectorUuid, String kind);

    List<ComplianceGroup> findByConnectorUuidAndKindAndGroupUuidIn(UUID connectorUuid, String kind, List<UUID> groupUuids);

    Optional<ComplianceGroup> findByUuidAndConnectorAndKind(UUID uuid, Connector connector, String kind);

    List<ComplianceGroup> findByConnector(Connector connector);
}

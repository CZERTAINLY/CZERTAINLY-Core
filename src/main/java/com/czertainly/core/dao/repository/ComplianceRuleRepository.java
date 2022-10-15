package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceRuleRepository extends SecurityFilterRepository<ComplianceRule, Long> {

    Optional<ComplianceRule> findByUuid(UUID uuid);

    List<ComplianceRule> findByUuidIn(List<UUID> uuid);

    Optional<ComplianceRule> findByName(String name);

    List<ComplianceRule> findByConnectorAndKind(Connector connector, String kind);

    List<ComplianceRule> findByConnectorAndKindAndCertificateTypeIn(Connector connector, String kind, List<CertificateType> certificateTypes);

    Optional<ComplianceRule> findByUuidAndConnectorAndKind(UUID uuid, Connector connector, String kind);

    List<ComplianceRule> findByConnector(Connector connector);
}

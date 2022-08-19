package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceRuleRepository extends SecurityFilterRepository<ComplianceRule, Long> {

    Optional<ComplianceRule> findByUuid(String uuid);

    List<ComplianceRule> findByUuidIn(List<String> uuid);

    Optional<ComplianceRule> findByName(String name);

    List<ComplianceRule> findByConnectorAndKind(Connector connector, String kind);

    List<ComplianceRule> findByConnectorAndKindAndCertificateTypeIn(Connector connector, String kind, List<CertificateType> certificateTypes);

    Optional<ComplianceRule> findByUuidAndConnectorAndKind(String uuid, Connector connector, String kind);

    List<ComplianceRule> findByConnector(Connector connector);
}

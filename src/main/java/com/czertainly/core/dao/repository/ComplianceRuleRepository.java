package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.ComplianceRule;
import com.czertainly.core.dao.entity.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceRuleRepository extends JpaRepository<ComplianceRule, Long> {

    Optional<ComplianceRule> findByUuid(String uuid);

    Optional<ComplianceRule> findByName(String name);

    List<ComplianceRule> findByConnectorAndKind(Connector connector, String kind);

    List<ComplianceRule> findByConnectorAndKindAndCertificateTypeIn(Connector connector, String kind, List<CertificateType> certificateTypes);

    Optional<ComplianceRule> findByUuidAndConnectorAndKind(String uuid, Connector connector, String kind);

    List<ComplianceRule> findByConnector(Connector connector);

    List<ComplianceRule> findByIdIn(List<Long> ids);
}

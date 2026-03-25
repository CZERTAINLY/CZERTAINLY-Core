package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CertificateRequestEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRequestRepository extends SecurityFilterRepository<CertificateRequestEntity, UUID> {

    Optional<CertificateRequestEntity> findByUuid(final UUID uuid);

    Optional<CertificateRequestEntity> findByUuidIn(final List<UUID> uuids);

    Optional<CertificateRequestEntity> findByFingerprint(final String fingerprint);

    @Modifying
    @Query(value = """
            WITH affected_by_profile AS (
                SELECT DISTINCT cr.uuid
                FROM {h-schema}certificate_request cr
                JOIN {h-schema}certificate c
                    ON cr.certificate_uuid = c.uuid
                JOIN {h-schema}ra_profile ra
                    ON c.ra_profile_uuid = ra.uuid
                JOIN {h-schema}compliance_profile_association cpa
                    ON cpa.object_uuid = ra.uuid
                JOIN {h-schema}compliance_profile_rule cpr
                    ON cpr.compliance_profile_uuid = cpa.compliance_profile_uuid
                WHERE cpa.resource = 'RA_PROFILE'
                  AND cpr.internal_rule_uuid = :ruleUuid
                  AND cr.compliance_status IN ('NOK', 'NA', 'FAILED')
            )
            UPDATE {h-schema}certificate_request cr
            SET compliance_result =
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            cr.compliance_result,
                            '{internalRules,notCompliant}',
                            COALESCE((cr.compliance_result #> '{internalRules,notCompliant}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                            true
                        ),
                        '{internalRules,notApplicable}',
                        COALESCE((cr.compliance_result #> '{internalRules,notApplicable}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                        true
                    ),
                    '{internalRules,notAvailable}',
                    COALESCE((cr.compliance_result #> '{internalRules,notAvailable}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                    true
                )
            WHERE NOT EXISTS (
                            SELECT 1
                            FROM affected_by_profile ap
                            WHERE ap.uuid = cr.uuid
                        )
                AND jsonb_exists(
                       COALESCE(cr.compliance_result -> 'internalRules' -> 'notCompliant', '{}'::jsonb)
                       || COALESCE(cr.compliance_result -> 'internalRules' -> 'notApplicable', '{}'::jsonb)
                       || COALESCE(cr.compliance_result -> 'internalRules' -> 'notAvailable', '{}'::jsonb),
                       CAST(:ruleUuid AS text)
                        )
            """, nativeQuery = true)
    void removeInternalRuleFromComplianceResult(@Param("ruleUuid") UUID ruleUuid);

    @Modifying
    @Query(value = """
            WITH affected_by_profile AS (
                SELECT DISTINCT cr.uuid
                FROM {h-schema}certificate_request cr
                JOIN {h-schema}certificate c
                    ON cr.certificate_uuid = c.uuid
                JOIN {h-schema}ra_profile ra
                    ON c.ra_profile_uuid = ra.uuid
                JOIN {h-schema}compliance_profile_association cpa
                    ON cpa.object_uuid = ra.uuid
                JOIN {h-schema}compliance_profile_rule cpr
                    ON cpr.compliance_profile_uuid = cpa.compliance_profile_uuid
                WHERE cpa.resource = 'RA_PROFILE'
                  AND cpr.compliance_rule_uuid = :ruleUuid
                  AND cpr.connector_uuid = :connectorUuid
                  AND cr.compliance_status IN ('NOK', 'NA', 'FAILED')
            )
            UPDATE {h-schema}certificate_request cr
            SET compliance_result =
                jsonb_set(
                             cr.compliance_result,
                             '{providerRules}',
                             (
                                 SELECT jsonb_agg(
                                     CASE
                                         WHEN pr ->> 'connectorUuid' = CAST(:connectorUuid AS text) THEN
                                             jsonb_set(
                                                 jsonb_set(
                                                     jsonb_set(
                                                         pr,
                                                         '{notCompliant}',
                                                         COALESCE((pr -> 'notCompliant') - CAST(:ruleUuid AS text), '[]'::jsonb),
                                                         true
                                                     ),
                                                     '{notApplicable}',
                                                     COALESCE((pr -> 'notApplicable') - CAST(:ruleUuid AS text), '[]'::jsonb),
                                                     true
                                                 ),
                                                 '{notAvailable}',
                                                 COALESCE((pr -> 'notAvailable') - CAST(:ruleUuid AS text), '[]'::jsonb),
                                                 true
                                             )
                                         ELSE pr
                                     END
                                 )
                                 FROM jsonb_array_elements(COALESCE(cr.compliance_result -> 'providerRules', '[]'::jsonb)) AS pr
                             ),
                             true
                 )
            WHERE NOT EXISTS (
                            SELECT 1
                            FROM affected_by_profile ap
                            WHERE ap.uuid = cr.uuid
                        )
                AND EXISTS (
                      SELECT 1
                      FROM jsonb_array_elements(cr.compliance_result -> 'providerRules') AS pr
                      WHERE pr ->> 'connectorUuid' = CAST(:connectorUuid AS text)
                        AND jsonb_exists(
                            COALESCE(pr -> 'notCompliant', '[]'::jsonb)
                            || COALESCE(pr -> 'notApplicable', '[]'::jsonb)
                            || COALESCE(pr -> 'notAvailable', '[]'::jsonb),
                       CAST(:ruleUuid AS text))
                  )
            """, nativeQuery = true)
    void removeProviderRuleFromComplianceResult(@Param("ruleUuid") UUID ruleUuid, @Param("connectorUuid") UUID connectorUuid);
}

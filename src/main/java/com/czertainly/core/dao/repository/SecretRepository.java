package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.Secret;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecretRepository extends SecurityFilterRepository<Secret, UUID> {

    boolean existsByName(String name);

    Optional<Secret> findByUuid(UUID uuid);

    @Query("SELECT s.name FROM Secret s WHERE s.sourceVaultProfileUuid = :sourceVaultProfileUuid")
    List<String> findAllNamesBySourceVaultProfileUuid(UUID sourceVaultProfileUuid);

    @Query("SELECT s.name FROM Secret s JOIN s.syncVaultProfiles svp JOIN svp.vaultProfile vp WHERE vp.uuid = :syncVaultProfileUuid")
    List<String> findAllNamesBySyncVaultProfileUuid(UUID syncVaultProfileUuid);

    @EntityGraph(attributePaths = {"groups", "owner", "sourceVaultProfile", "latestVersion", "syncVaultProfiles"})
    Optional<Secret> findWithAssociationsByUuid(UUID uuid);

    List<Secret> findByUuidIn(List<UUID> objectUuids);

    List<Secret> findBySourceVaultProfileUuid(UUID associationObjectUuid);

    List<Secret> findByUuidIn(List<UUID> objectUuids);

    List<Secret> findBySourceVaultProfileUuid(UUID associationObjectUuid);

    @Modifying
    @Query(value = """
            WITH affected_by_profile AS (
                SELECT DISTINCT s.uuid
                FROM {h-schema}secret s
                JOIN {h-schema}vault_profile va
                    ON s.vault_profile_uuid = va.uuid
                JOIN {h-schema}compliance_profile_association cpa
                    ON cpa.object_uuid = va.uuid
                JOIN {h-schema}compliance_profile_rule cpr
                    ON cpr.compliance_profile_uuid = cpa.compliance_profile_uuid
                WHERE cpa.resource = 'VAULT_PROFILE'
                  AND cpr.internal_rule_uuid = :ruleUuid
                  AND s.compliance_status IN ('NOK', 'NA', 'FAILED')
            )
            UPDATE {h-schema}secret s
            SET compliance_result =
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            s.compliance_result,
                            '{internalRules,notCompliant}',
                            COALESCE((s.compliance_result #> '{internalRules,notCompliant}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                            true
                        ),
                        '{internalRules,notApplicable}',
                        COALESCE((s.compliance_result #> '{internalRules,notApplicable}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                        true
                    ),
                    '{internalRules,notAvailable}',
                    COALESCE((s.compliance_result #> '{internalRules,notAvailable}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                    true
                )
            WHERE NOT EXISTS (
                            SELECT 1
                            FROM affected_by_profile ap
                            WHERE ap.uuid = s.uuid
                        )
                AND jsonb_exists(
                       COALESCE(s.compliance_result -> 'internalRules' -> 'notCompliant', '{}'::jsonb)
                       || COALESCE(s.compliance_result -> 'internalRules' -> 'notApplicable', '{}'::jsonb)
                       || COALESCE(s.compliance_result -> 'internalRules' -> 'notAvailable', '{}'::jsonb),
                       CAST(:ruleUuid AS text)
                        )
            """, nativeQuery = true)
    void removeInternalRuleFromComplianceResult(@Param("ruleUuid") UUID ruleUuid);

    @Modifying
    @Query(value = """
            WITH affected_by_profile AS (
                SELECT DISTINCT s.uuid
                FROM {h-schema}secret s
                JOIN {h-schema}vault_profile va
                    ON s.vault_profile_uuid = va.uuid
                JOIN {h-schema}compliance_profile_association cpa
                    ON cpa.object_uuid = va.uuid
                JOIN {h-schema}compliance_profile_rule cpr
                    ON cpr.compliance_profile_uuid = cpa.compliance_profile_uuid
                WHERE cpa.resource = 'VAULT_PROFILE'
                  AND cpr.compliance_rule_uuid = :ruleUuid
                  AND cpr.connector_uuid = :connectorUuid
                  AND s.compliance_status IN ('NOK', 'NA', 'FAILED')
            )
            UPDATE {h-schema}secret s
            SET compliance_result =
                jsonb_set(
                             s.compliance_result,
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
                                 FROM jsonb_array_elements(COALESCE(s.compliance_result -> 'providerRules', '[]'::jsonb)) AS pr
                             ),
                             true
                 )
            WHERE NOT EXISTS (
                            SELECT 1
                            FROM affected_by_profile ap
                            WHERE ap.uuid = s.uuid
                        )
                AND EXISTS (
                      SELECT 1
                      FROM jsonb_array_elements(s.compliance_result -> 'providerRules') AS pr
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

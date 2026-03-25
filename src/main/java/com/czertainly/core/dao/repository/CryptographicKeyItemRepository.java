package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptographicKeyItemRepository extends SecurityFilterRepository<CryptographicKeyItem, UUID> {

    Optional<CryptographicKeyItem> findByUuid(UUID uuid);

    Optional<CryptographicKeyItem> findByFingerprint(String fingerprint);

    Optional<CryptographicKeyItem> findByUuidAndKey(UUID uuid, CryptographicKey cryptographicKey);

    @EntityGraph(attributePaths = {"key", "key.tokenProfile"})
    List<CryptographicKeyItem> findByUuidIn(List<UUID> uuids);

    @EntityGraph(attributePaths = {"key", "key.tokenProfile", "key.groups", "key.owner"})
    List<CryptographicKeyItem> findFullByUuidInOrderByCreatedAtDesc(List<UUID> uuids);

    @EntityGraph(attributePaths = {"key", "key.items"})
    List<CryptographicKeyItem> findWithKeyByUuidIn(List<UUID> uuids);

    List<CryptographicKeyItem> findByKeyUuidIn(List<UUID> keyUuids);

    List<CryptographicKeyItem> findByKeyReferenceUuid(UUID keyReferenceUuid);

    List<CryptographicKeyItem> findByKeyTokenProfileUuid(UUID tokenProfileUuid);

    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}cryptographic_key_item (
                uuid, name, type, key_reference_uuid, key_uuid, key_algorithm, format, key_data,
                state, enabled, length, fingerprint, reason, compliance_status, created_at, updated_at, usage
            ) VALUES (
                :#{#cki.uuid}, :#{#cki.name}, :#{#cki.type.name()}, :#{#cki.keyReferenceUuid}, :#{#cki.keyUuid},
                :#{#cki.keyAlgorithm.name()}, :#{#cki.format?.name() ?: null}, :#{#cki.keyData}, :#{#cki.state.name()}, :#{#cki.enabled},
                :#{#cki.length}, :#{#cki.fingerprint}, :#{#cki.reason?.name() ?: null}, :#{#cki.complianceStatus.name()}, :#{#cki.createdAt},
                :#{#cki.updatedAt}, :#{#cki.usageBitmask}
            ) ON CONFLICT (fingerprint) DO NOTHING
            """, nativeQuery = true)
    void insertWithFingerprintConflictResolve(@Param("cki") CryptographicKeyItem keyItem);

    @Query(value = """
            SELECT COUNT(c.uuid)
                FROM CryptographicKeyItem cki
                JOIN cki.key ck
                LEFT JOIN Certificate c
                    ON c.keyUuid = ck.uuid
                    OR c.altKeyUuid = ck.uuid
                WHERE cki.uuid IN :uuids
                GROUP BY cki.uuid
                ORDER BY cki.createdAt DESC
            """)
    List<Integer> getCountsOfAssociations(@Param("uuids") List<UUID> uuids);

    @Modifying
    @Query(value = """
            WITH affected_by_profile AS (
                SELECT DISTINCT cki.uuid
                FROM {h-schema}cryptographic_key_item cki
                JOIN {h-schema}cryptographic_key ck
                    ON cki.key_uuid = ck.uuid
                JOIN {h-schema}token_profile tp
                    ON ck.token_profile_uuid = tp.uuid
                JOIN {h-schema}compliance_profile_association cpa
                    ON cpa.object_uuid = tp.uuid
                JOIN {h-schema}compliance_profile_rule cpr
                    ON cpr.compliance_profile_uuid = cpa.compliance_profile_uuid
                WHERE cpa.resource = 'TOKEN_PROFILE'
                  AND cpr.internal_rule_uuid = :ruleUuid
                  AND s.compliance_status IN ('NOK', 'NA', 'FAILED')
            )
            UPDATE {h-schema}cryptographic_key_item cki
            SET compliance_result =
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            cki.compliance_result,
                            '{internalRules,notCompliant}',
                            COALESCE((cki.compliance_result #> '{internalRules,notCompliant}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                            true
                        ),
                        '{internalRules,notApplicable}',
                        COALESCE((cki.compliance_result #> '{internalRules,notApplicable}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                        true
                    ),
                    '{internalRules,notAvailable}',
                    COALESCE((cki.compliance_result #> '{internalRules,notAvailable}') - CAST(:ruleUuid AS text), '[]'::jsonb),
                    true
                )
            WHERE NOT EXISTS (
                            SELECT 1
                            FROM affected_by_profile ap
                            WHERE ap.uuid = cki.uuid
                        )
                AND jsonb_exists(
                       COALESCE(cki.compliance_result -> 'internalRules' -> 'notCompliant', '{}'::jsonb)
                       || COALESCE(cki.compliance_result -> 'internalRules' -> 'notApplicable', '{}'::jsonb)
                       || COALESCE(cki.compliance_result -> 'internalRules' -> 'notAvailable', '{}'::jsonb),
                       CAST(:ruleUuid AS text)
                        )
            """, nativeQuery = true)
    void removeInternalRuleFromComplianceResult(@Param("ruleUuid") UUID ruleUuid);

    @Modifying
    @Query(value = """
            WITH affected_by_profile AS (
                SELECT DISTINCT cki.uuid
                FROM {h-schema}cryptographic_key_item cki
                JOIN {h-schema}cryptographic_key ck
                    ON cki.key_uuid = ck.uuid
                JOIN {h-schema}token_profile tp
                    ON ck.token_profile_uuid = tp.uuid
                JOIN {h-schema}compliance_profile_association cpa
                    ON cpa.object_uuid = tp.uuid
                JOIN {h-schema}compliance_profile_rule cpr
                    ON cpr.compliance_profile_uuid = cpa.compliance_profile_uuid
                WHERE cpa.resource = 'TOKEN_PROFILE'
                  AND cpr.compliance_rule_uuid = :ruleUuid
                  AND cpr.connector_uuid = :connectorUuid
                  AND cki.compliance_status IN ('NOK', 'NA', 'FAILED')
            )
            UPDATE {h-schema}cryptographic_key_item cki
            SET compliance_result =
                jsonb_set(
                             cki.compliance_result,
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
                                 FROM jsonb_array_elements(COALESCE(cki.compliance_result -> 'providerRules', '[]'::jsonb)) AS pr
                             ),
                             true
                 )
            WHERE NOT EXISTS (
                            SELECT 1
                            FROM affected_by_profile ap
                            WHERE ap.uuid = cki.uuid
                        )
                AND EXISTS (
                      SELECT 1
                      FROM jsonb_array_elements(cki.compliance_result -> 'providerRules') AS pr
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

package db.migration;

import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.compliance.v2.ComplianceResultDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceResultRulesDto;
import com.czertainly.core.util.DatabaseMigration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Migration script for the Json array migration to separate table
 */
public class V202507311051__MigrateToComplianceProfilesV2 extends BaseJavaMigration {

    private static final Logger logger = LoggerFactory.getLogger(V202507311051__MigrateToComplianceProfilesV2.class);

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202507311051__MigrateToComplianceProfilesV2.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {
        prepareDBStructure(context);
        migrateData(context);
        cleanDbStructureAndData(context);
    }

    private void prepareDBStructure(Context context) throws SQLException {
        String sqlCommands = """
                ALTER TABLE compliance_rule
                    ADD COLUMN rule_uuid UUID NULL,
                    ADD COLUMN resource TEXT NULL,
                    ADD COLUMN type TEXT NULL,
                    --ADD COLUMN availability_status TEXT NULL,
                    ALTER COLUMN attributes TYPE JSONB USING attributes::jsonb,
                    ALTER COLUMN name TYPE TEXT,
                    ALTER COLUMN kind TYPE TEXT,
                    ALTER COLUMN description TYPE TEXT,
                    DROP COLUMN decommissioned,
                    DROP COLUMN certificate_type,
                    DROP CONSTRAINT "compliance_rule_to_compliance_group_key";
                
                UPDATE compliance_rule SET resource = 'CERTIFICATE', rule_uuid = uuid;
                --UPDATE compliance_rule SET availability_status = 'AVAILABLE';
                ALTER TABLE compliance_rule
                  --ALTER COLUMN availability_status SET NOT NULL,
                    ALTER COLUMN resource SET NOT NULL,
                    ALTER COLUMN rule_uuid SET NOT NULL;
                
                ALTER TABLE compliance_group
                    ADD COLUMN group_uuid UUID NULL,
                    ADD COLUMN resource TEXT NULL,
                    ALTER COLUMN name TYPE TEXT,
                    ALTER COLUMN kind TYPE TEXT,
                    DROP COLUMN decommissioned,
                    DROP CONSTRAINT "compliance_group_to_connector",
                    ADD CONSTRAINT fk_compliance_group_to_connector FOREIGN KEY (connector_uuid) REFERENCES connector(uuid) ON UPDATE CASCADE ON DELETE NO ACTION;
                
                UPDATE compliance_group SET resource = 'CERTIFICATE', group_uuid = uuid;
                ALTER TABLE compliance_group
                    ALTER COLUMN group_uuid SET NOT NULL;
                
                ALTER TABLE compliance_profile_rule
                    ADD COLUMN compliance_rule_uuid UUID NULL,
                    ADD COLUMN compliance_group_uuid UUID NULL,
                    ADD COLUMN internal_rule_uuid UUID NULL,
                    ALTER COLUMN attributes TYPE JSONB USING attributes::jsonb,
                    DROP COLUMN i_author,
                    DROP COLUMN i_cre,
                    DROP COLUMN i_upd,
                    DROP CONSTRAINT "compliance_profile_rule_to_compliance_profile",
                    ADD CONSTRAINT fk_compliance_profile_rule_to_compliance_profile FOREIGN KEY (compliance_profile_uuid) REFERENCES compliance_profile(uuid) ON UPDATE CASCADE ON DELETE CASCADE,
                    ADD CONSTRAINT fk_compliance_profile_rule_to_compliance_rule FOREIGN KEY (compliance_rule_uuid) REFERENCES compliance_rule(uuid) ON UPDATE CASCADE ON DELETE RESTRICT,
                    ADD CONSTRAINT fk_compliance_profile_rule_to_compliance_group FOREIGN KEY (compliance_group_uuid) REFERENCES compliance_group(uuid) ON UPDATE CASCADE ON DELETE RESTRICT,
                    ADD CONSTRAINT fk_compliance_profile_rule_to_rule FOREIGN KEY (internal_rule_uuid) REFERENCES rule(uuid) ON UPDATE CASCADE ON DELETE RESTRICT;
                
                UPDATE compliance_profile_rule SET compliance_rule_uuid = rule_uuid;
                ALTER TABLE compliance_profile_rule
                    DROP COLUMN rule_uuid;
                
                CREATE TABLE compliance_profile_association
                (
                    "uuid"                      UUID    NOT NULL,
                    "compliance_profile_uuid"   UUID    NOT NULL,
                    "resource"                  TEXT    NOT NULL,
                    "object_uuid"               UUID    NOT NULL,
                    PRIMARY KEY ("uuid")
                );
                
                ALTER TABLE compliance_profile_association
                    ADD CONSTRAINT fk_compliance_profile_association_to_compliance_profile FOREIGN KEY (compliance_profile_uuid) REFERENCES compliance_profile(uuid) ON UPDATE CASCADE ON DELETE RESTRICT;
                
                ALTER TABLE compliance_profile
                    ADD COLUMN version TEXT NULL;
                
                UPDATE compliance_profile SET version = 'v1';
                ALTER TABLE compliance_profile
                    ALTER COLUMN version SET NOT NULL;
                
                UPDATE certificate SET compliance_result = NULL WHERE compliance_result = 'null' OR compliance_result = 'NULL';
                ALTER TABLE certificate
                    ALTER COLUMN compliance_result TYPE JSONB USING compliance_result::jsonb;
                
                ALTER TABLE certificate_request
                    ADD COLUMN compliance_status TEXT NULL,
                    ADD COLUMN compliance_result JSONB NULL;

                UPDATE certificate_request SET compliance_status = 'NOT_CHECKED';
                ALTER TABLE certificate_request
                    ALTER COLUMN compliance_status SET NOT NULL;

                ALTER TABLE cryptographic_key
                    ADD COLUMN compliance_status TEXT NULL,
                    ADD COLUMN compliance_result JSONB NULL;
                
                UPDATE cryptographic_key SET compliance_status = 'NOT_CHECKED';
                ALTER TABLE cryptographic_key
                    ALTER COLUMN compliance_status SET NOT NULL;
                """;
        try (final Statement statement = context.getConnection().createStatement()) {
            statement.execute(sqlCommands);
        }
    }

    private void migrateData(Context context) throws SQLException, CertificateOperationException {
        try (final Statement statement = context.getConnection().createStatement();
             final PreparedStatement updateCertComplianceResultStatement = context.getConnection().prepareStatement("UPDATE certificate SET compliance_result = ?::jsonb WHERE uuid = ?");
             final PreparedStatement insertProfileAssocStatement = context.getConnection().prepareStatement("INSERT INTO compliance_profile_association(uuid, compliance_profile_uuid, resource, object_uuid) VALUES (?,?,?,?)");
             final PreparedStatement insertProfileGroupAssocStatement = context.getConnection().prepareStatement("INSERT INTO compliance_profile_rule(uuid, compliance_profile_uuid, compliance_group_uuid) VALUES (?,?,?)")) {

            // migrate RA profile associations
            int count = 0;
            try (ResultSet rows = statement.executeQuery("SELECT ra_profile_uuid, compliance_profile_uuid FROM ra_profile_2_compliance_profile")) {
                while (rows.next()) {
                    final UUID raPofileUuid = rows.getObject("ra_profile_uuid", UUID.class);
                    final UUID compliancePofileUuid = rows.getObject("compliance_profile_uuid", UUID.class);

                    insertProfileAssocStatement.setObject(1, UUID.randomUUID());
                    insertProfileAssocStatement.setObject(2, compliancePofileUuid);
                    insertProfileAssocStatement.setString(3, Resource.RA_PROFILE.name());
                    insertProfileAssocStatement.setObject(4, raPofileUuid);
                    insertProfileAssocStatement.addBatch();
                    ++count;
                }
            }
            logger.debug("Executing batch insert with {} compliance profile associations.", count);
            insertProfileAssocStatement.executeBatch();

            // migrate compliance groups to compliance profile associations
            count = 0;
            try (ResultSet rows = statement.executeQuery("SELECT profile_uuid, group_uuid FROM compliance_profile_2_compliance_group")) {
                while (rows.next()) {
                    final UUID groupUuid = rows.getObject("group_uuid", UUID.class);
                    final UUID compliancePofileUuid = rows.getObject("profile_uuid", UUID.class);

                    insertProfileGroupAssocStatement.setObject(1, UUID.randomUUID());
                    insertProfileGroupAssocStatement.setObject(2, compliancePofileUuid);
                    insertProfileGroupAssocStatement.setObject(3, groupUuid);
                    insertProfileGroupAssocStatement.addBatch();
                    ++count;
                }
            }
            logger.debug("Executing batch insert with {} compliance profile group associations.", count);
            insertProfileGroupAssocStatement.executeBatch();

            // migrate compliance results
            count = 0;
            ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY)
                    .modules(new JavaTimeModule())
                    .serializationInclusion(JsonInclude.Include.NON_NULL)
                    .build();
            OffsetDateTime complianceTimestamp = OffsetDateTime.now();
            try (ResultSet rows = statement.executeQuery("SELECT uuid, compliance_status, compliance_result FROM certificate WHERE compliance_result IS NOT NULL")) {
                while (rows.next()) {
                    final UUID certificateUuid = rows.getObject("uuid", UUID.class);
                    final ComplianceStatus complianceStatus = ComplianceStatus.valueOf(rows.getString("compliance_status"));
                    String complianceResult = rows.getString("compliance_result");
                    try {
                        HashMap<String, List<String>> complianceResultOld = mapper.readValue(complianceResult, HashMap.class);
                        ComplianceResultRulesDto providerRulesResult = new ComplianceResultRulesDto();
                        providerRulesResult.getNotApplicable().addAll(complianceResultOld.get("na").stream().map(UUID::fromString).toList());
                        providerRulesResult.getNotCompliant().addAll(complianceResultOld.get("nok").stream().map(UUID::fromString).toList());

                        ComplianceResultDto complianceResultDto = new ComplianceResultDto();
                        complianceResultDto.setTimestamp(complianceTimestamp);
                        complianceResultDto.setStatus(complianceStatus);
                        complianceResultDto.setProviderRules(providerRulesResult);

                        updateCertComplianceResultStatement.setString(1, mapper.writeValueAsString(complianceResultDto));
                        updateCertComplianceResultStatement.setObject(2, certificateUuid);
                        updateCertComplianceResultStatement.addBatch();
                        ++count;
                    } catch (JsonProcessingException e) {
                        throw new CertificateOperationException("Failed to parse compliance result for certificate with UUID " + certificateUuid);
                    }
                }
            }

            logger.debug("Executing batch update of compliance result for {} certificates.", count);
            updateCertComplianceResultStatement.executeBatch();
        }
    }

    private void cleanDbStructureAndData(Context context) throws SQLException {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.addBatch("DROP TABLE compliance_profile_2_compliance_group;");
            statement.addBatch("DROP TABLE ra_profile_2_compliance_profile;");
            statement.executeBatch();

        }
    }
}
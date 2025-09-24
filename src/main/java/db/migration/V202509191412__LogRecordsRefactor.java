package db.migration;

import com.czertainly.core.util.DatabaseMigration;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("java:S101")
public class V202509191412__LogRecordsRefactor extends BaseJavaMigration {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202509191412__LogRecordsRefactor.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {
        String addTimestampToLogRecord = """
                UPDATE audit_log SET log_record =
                log_record::jsonb || jsonb_build_object(
                  'timestamp',
                  to_char(logged_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MSOF:TZM')
                )
                """;
        String removeResourceUuids = "UPDATE audit_log SET log_record = log_record::jsonb #- '{resource, uuids}'";
        String removeResourceNames = "UPDATE audit_log SET log_record = log_record::jsonb #- '{resource, names}'";
        String removeAffResourceUuids = "UPDATE audit_log SET log_record = log_record::jsonb #- '{affiliatedResource, uuids}'";
        String removeAffResourceNames = "UPDATE audit_log SET log_record = log_record::jsonb #- '{affiliatedResource, names}'";
        String addResourceIdentities =  "UPDATE audit_log SET log_record = jsonb_insert(log_record, '{resource, objects}', ?::jsonb) WHERE id = ?";
        String addAffResourceIdentities =  "UPDATE audit_log SET log_record = jsonb_insert(log_record, '{affiliatedResource, objects}', ?::jsonb) WHERE id = ?";

        try (Statement statement = context.getConnection().createStatement();
             PreparedStatement addResourceIdentitiesPs = context.getConnection().prepareStatement(addResourceIdentities);
             PreparedStatement addAffResourceIdentitiesPs = context.getConnection().prepareStatement(addAffResourceIdentities)
        ) {
            statement.execute("ALTER TABLE audit_log ADD COLUMN timestamp TIMESTAMP WITHOUT TIME ZONE");
            statement.execute("UPDATE audit_log SET timestamp = logged_at");
            statement.execute("ALTER TABLE audit_log ALTER COLUMN timestamp SET NOT NULL");
            statement.execute(addTimestampToLogRecord);
            statement.execute("UPDATE audit_log SET version = '1.1'");
            statement.setFetchSize(100000);
            ResultSet auditLogs = statement.executeQuery("""
                    SELECT id,
                    log_record #> '{resource, names}' as resourceNames,
                    log_record #> '{resource, uuids}' as resourceUuids,
                    log_record #> '{affiliatedResource, names}' as affResourceNames,
                    log_record #> '{affiliatedResource, uuids}' as affResourceUuids
                    FROM audit_log
                    WHERE log_record #> '{resource, names}' != 'null' OR log_record #> '{resource, uuids}' != 'null'
                       OR log_record #> '{affiliatedResource, names}' != 'null' OR log_record #> '{affiliatedResource, uuids}' != 'null';
                    """);
            while (auditLogs.next()) {
                addResourceIdentities(auditLogs, addResourceIdentitiesPs, "resourceNames", "resourceUuids");
                addResourceIdentities(auditLogs, addAffResourceIdentitiesPs, "affResourceNames", "affResourceUuids");
            }
            addResourceIdentitiesPs.executeBatch();
            addAffResourceIdentitiesPs.executeBatch();
            statement.execute(removeResourceNames);
            statement.execute(removeResourceUuids);
            statement.execute(removeAffResourceNames);
            statement.execute(removeAffResourceUuids);
        }
    }

    private static void addResourceIdentities(ResultSet auditLogs, PreparedStatement preparedStatement, String resourceNamesColumn, String resourceUuidsColumn) throws SQLException, IOException {
        List<String> resourceNames = MetaDefinitions.deserializeArrayString(auditLogs.getString(resourceNamesColumn));
        List<String> resourceUuids = MetaDefinitions.deserializeArrayString(auditLogs.getString(resourceUuidsColumn));
        if (resourceNames != null || resourceUuids != null) {
            String objectIdentities = getObjectIdentities(resourceNames == null ? new ArrayList<>() : resourceNames, resourceUuids == null ? new ArrayList<>() : resourceUuids);
            preparedStatement.setString(1, objectIdentities);
            preparedStatement.setInt(2, auditLogs.getInt("id"));
            preparedStatement.addBatch();
        }
    }


    public static String getObjectIdentities(List<String> resourceNames, List<String> resourceUuids) throws IOException {
        int maxSize = Integer.max(resourceNames.size(), resourceUuids.size());
        List<Map<String, String>> objectIdentifiers  = new ArrayList<>();
        for (int i = 0; i < maxSize; i++) {
            String name = resourceNames.size() <= i ? null : resourceNames.get(i);
            String uuid = resourceUuids.size() <= i ? null : resourceUuids.get(i);
            if (!(name == null && uuid == null)) {
                Map<String, String> identity = new HashMap<>();
                identity.put("name", name);
                identity.put("uuid", uuid);
                objectIdentifiers.add(identity);
            }
        }
        return mapper.writeValueAsString(objectIdentifiers);
    }
}

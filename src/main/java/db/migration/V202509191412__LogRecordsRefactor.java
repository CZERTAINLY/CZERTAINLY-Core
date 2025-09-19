package db.migration;

import com.czertainly.core.util.DatabaseMigration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@SuppressWarnings("java:S101")
public class V202509191412__LogRecordsRefactor extends BaseJavaMigration {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202509191412__LogRecordsRefactor.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {
        String updateAuditLog = "UPDATE audit_log SET log_record = ?::jsonb WHERE id = ?";
        try (Statement statement = context.getConnection().createStatement();
             PreparedStatement updateStatement = context.getConnection().prepareStatement(updateAuditLog)) {
            ResultSet auditLogs = statement.executeQuery("SELECT id, log_record FROM audit_log");
            while (auditLogs.next()) {
                String newJson = changeLogRecordToNewVersion(auditLogs.getString("log_record"));
                updateStatement.setString(1, newJson);
                updateStatement.setInt(2, auditLogs.getInt("id"));
                updateStatement.addBatch();
            }
            updateStatement.executeBatch();
            statement.execute("UPDATE audit_log SET version = '1.1'");
        }
    }


    public static String changeLogRecordToNewVersion(String oldJson) throws IOException {
        JsonNode root = mapper.readTree(oldJson);
        // migrate "resource"
        updateResource(root, "resource");
        // migrate "affiliatedResource" if present
        updateResource(root, "affiliatedResource");
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private static void updateResource(JsonNode root, String resource) {
        JsonNode resourceNode = root.get(resource);
        if (resourceNode != null && resourceNode.isObject()) {
            ObjectNode resourceObj = (ObjectNode) resourceNode;
            // collect names + uuids
            JsonNode names = resourceObj.get("names");
            JsonNode uuids = resourceObj.get("uuids");

            ArrayNode objects = mapper.createArrayNode();

            int maxSize = 0;
            if (names != null && names.isArray()) {
                maxSize = Math.max(maxSize, names.size());
            }
            if (uuids != null && uuids.isArray()) {
                maxSize = Math.max(maxSize, uuids.size());
            }

            for (int i = 0; i < maxSize; i++) {
                ObjectNode obj = mapper.createObjectNode();
                if (names != null && i < names.size() && !names.get(i).isNull()) {
                    obj.put("name", names.get(i).asText());
                } else {
                    obj.putNull("name");
                }
                if (uuids != null && i < uuids.size() && !uuids.get(i).isNull()) {
                    obj.put("uuid", uuids.get(i).asText());
                } else {
                    obj.putNull("uuid");
                }
                objects.add(obj);
            }

            // replace old fields
            resourceObj.remove("names");
            resourceObj.remove("uuids");
            resourceObj.set("objects", objects);
        }
    }
}

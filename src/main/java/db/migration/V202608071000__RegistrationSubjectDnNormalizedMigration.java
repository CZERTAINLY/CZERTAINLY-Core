package db.migration;

import com.otilm.api.model.core.oid.OidCategory;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.oid.OidRecord;
import com.otilm.core.util.CertificateUtil;
import com.otilm.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Backfills the normalized subject of registration placeholders so the registration-mode enrolment lookup
 * can prefilter by it in SQL. PENDING_REGISTRATION rows are included because they transition to REGISTERED
 * without their subject fields being rewritten. A stored DN that does not re-parse stays NULL: the identity
 * match skips such rows, and a NULL normalized subject keeps the SQL prefilter equally unable to return them.
 */
public class V202608071000__RegistrationSubjectDnNormalizedMigration extends BaseJavaMigration {

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202608071000__RegistrationSubjectDnNormalizedMigration.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {
        seedRdnOidRegistry(context);
        backfillNormalizedSubjects(context);
    }

    /**
     * Flyway runs before the Spring context populates the process-wide OID registry, so custom RDN codes
     * would be unresolvable and their DNs skipped as unparseable. Seed the RDN category from the same
     * database so the backfill parses stored DNs exactly as the runtime identity match does.
     */
    private void seedRdnOidRegistry(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement();
             ResultSet rows = select.executeQuery(
                     "SELECT oid, display_name, code, alt_codes FROM custom_oid_entry WHERE category = 'RDN_ATTRIBUTE_TYPE'")) {
            while (rows.next()) {
                Array altCodes = rows.getArray("alt_codes");
                OidHandler.cacheOid(OidCategory.RDN_ATTRIBUTE_TYPE, rows.getString("oid"), OidRecord.builder()
                        .displayName(rows.getString("display_name"))
                        .code(rows.getString("code"))
                        .altCodes(altCodes == null ? List.of() : Arrays.stream((String[]) altCodes.getArray())
                                .filter(Objects::nonNull)
                                .toList())
                        .build());
            }
        }
    }

    private void backfillNormalizedSubjects(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement();
             PreparedStatement update = context.getConnection().prepareStatement(
                     "UPDATE certificate SET subject_dn_normalized = ? WHERE uuid = ?");
             ResultSet rows = select.executeQuery(
                     "SELECT uuid, subject_dn FROM certificate "
                             + "WHERE state IN ('REGISTERED', 'PENDING_REGISTRATION') AND subject_dn_normalized IS NULL")) {
            while (rows.next()) {
                String normalized;
                try {
                    normalized = CertificateUtil.normalizeStoredSubjectDn(rows.getString("subject_dn"));
                } catch (RuntimeException e) {
                    continue;
                }
                update.setString(1, normalized);
                update.setObject(2, UUID.fromString(rows.getString("uuid")));
                update.executeUpdate();
            }
        }
    }
}

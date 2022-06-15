package db.migration;

import com.czertainly.core.util.AttributeMigrationUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;

import org.flywaydb.core.internal.resource.filesystem.*;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class V202206151000__AttributeChanges extends BaseJavaMigration {
    //Checksum to be added for the java migration. Derived from the time when the script is made and negated
    private static final String CREDENTIAL_TABLE_NAME = "credential";
    private static final String ACME_TABLE_NAME = "acme_profile";
    private static final String RA_TABLE_NAME = "ra_profile";
    private static final String DISCOVERY_TABLE_NAME = "discovery_history";
    private static final String LOCATION_TABLE_NAME = "location";
    private static final String CERTIFICATE_LOCATION_TABLE_NAME = "certificate_location";

    private static final String ATTRIBUTE_COLUMN_NAME = "attributes";
    @Override
    public Integer getChecksum(){
        ClassLoader loader = V202206151000__AttributeChanges.class.getClassLoader();
        try {
            BufferedInputStream a = (BufferedInputStream) loader.getResource("db/migration/V202206151000__AttributeChanges.class").getContent();
            byte[] contents = new byte[1024];
            int bytesRead;
            String strFileContents = "";
            while (true) {
                if (!((bytesRead = a.read(contents)) != -1)) break;
                strFileContents += new String(contents, 0, bytesRead);
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter("test.class"));
            writer.write(strFileContents);
            writer.close();

            FileSystemResource r = new FileSystemResource(null, "test.class", StandardCharsets.UTF_8);
            Integer checksum = ChecksumCalculator.calculate(r);
            File tempFile = new File("test.class");
            tempFile.delete();
            return checksum;

        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public void migrate(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            applyCredentialMigration(context);
            applyRaProfileMigration(context);
            applyDiscoveryHistoryMigration(context);
            applyAcmeProfileMigration(context);
        }
    }

    private void applyCredentialMigration(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, attributes FROM credential ORDER BY id")) {
                List<String> migrationCommands = AttributeMigrationUtils.getMigrationCommands(rows, CREDENTIAL_TABLE_NAME, ATTRIBUTE_COLUMN_NAME);
                executeCommands(select, migrationCommands);
            }
        }
    }

    private void applyLocationMigration(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, attributes FROM location ORDER BY id")) {
                List<String> migrationCommands = AttributeMigrationUtils.getMigrationCommands(rows, LOCATION_TABLE_NAME, ATTRIBUTE_COLUMN_NAME);
                executeCommands(select, migrationCommands);
            }
        }
    }

    private void applyCertificateLocationMigration(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("select csr_attributes, push_attributes FROM certificate_location")) {
                List<String> migrationCommands = AttributeMigrationUtils.getMigrationCommands(rows, CERTIFICATE_LOCATION_TABLE_NAME, "push_attributes");
                List<String> csrCommands = AttributeMigrationUtils.getMigrationCommands(rows, CERTIFICATE_LOCATION_TABLE_NAME, "csr_attributes");
                executeCommands(select, migrationCommands);
                executeCommands(select, csrCommands);
            }
        }
    }

    private void applyDiscoveryHistoryMigration(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, attributes FROM discovery_history ORDER BY id")) {
                List<String> migrationCommands = AttributeMigrationUtils.getMigrationCommands(rows, DISCOVERY_TABLE_NAME, ATTRIBUTE_COLUMN_NAME);
                executeCommands(select, migrationCommands);
            }
        }
    }

    private void applyRaProfileMigration(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, attributes,acme_issue_certificate_attributes,acme_revoke_certificate_attributes FROM ra_profile ORDER BY id")) {
                List<String> raProfileCommands = AttributeMigrationUtils.getMigrationCommands(rows, RA_TABLE_NAME, ATTRIBUTE_COLUMN_NAME);
                List<String> raProfileIssueCommands = AttributeMigrationUtils.getMigrationCommands(rows, RA_TABLE_NAME, "acme_issue_certificate_attributes");
                List<String> raProfileRevokeCommands = AttributeMigrationUtils.getMigrationCommands(rows, RA_TABLE_NAME, "acme_revoke_certificate_attributes");
                executeCommands(select, raProfileCommands);
                executeCommands(select, raProfileIssueCommands);
                executeCommands(select, raProfileRevokeCommands);
            }
        }
    }

    private void applyAcmeProfileMigration(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, issue_certificate_attributes, revoke_certificate_attributes FROM acme_profile ORDER BY id")) {
                List<String> acmeProfileIssueCommands = AttributeMigrationUtils.getMigrationCommands(rows, ACME_TABLE_NAME, "issue_certificate_attributes");
                List<String> acmeProfileRevokeCommands = AttributeMigrationUtils.getMigrationCommands(rows, ACME_TABLE_NAME, "revoke_certificate_attributes");
                executeCommands(select, acmeProfileIssueCommands);
                executeCommands(select, acmeProfileRevokeCommands);
            }
        }
    }

    private void executeCommands(Statement select, List<String> commands) throws SQLException {
        for(String command: commands) {
            select.execute(command);
        }
    }
}
package db.migration;

import com.czertainly.api.model.core.certificate.CertificateRelationType;
import com.czertainly.core.util.DatabaseMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;

@SuppressWarnings("java:S101")
public class V202508130940__CertificateRelations extends BaseJavaMigration {

    public static final String KEY_UUID = "key_uuid";

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202508130940__CertificateRelations.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {

        String updateCertificateRelations = """
                    INSERT INTO certificate_relation
                    (certificate_uuid, source_certificate_uuid, relation_type, created_at)
                    VALUES
                    (?, ?, ?, NOW())
                    """;
        try (final Statement statement = context.getConnection().createStatement();
             final PreparedStatement preparedStatement = context.getConnection().prepareStatement(updateCertificateRelations)) {
            String createRelationTable = """
            CREATE TABLE certificate_relation (
                     certificate_uuid UUID NOT NULL,
                     source_certificate_uuid UUID NOT NULL,
                     relation_type TEXT,
                     created_at TIMESTAMP WITH TIME ZONE,
            
                     CONSTRAINT pk_certificate_relation PRIMARY KEY (certificate_uuid, source_certificate_uuid),
            
                     CONSTRAINT fk_certificate_relation_certificate
                         FOREIGN KEY (certificate_uuid)
                         REFERENCES certificate (uuid)
                         ON UPDATE CASCADE
                         ON DELETE CASCADE,
            
                     CONSTRAINT fk_certificate_relation_source_certificate
                         FOREIGN KEY (source_certificate_uuid)
                         REFERENCES certificate (uuid)
                         ON UPDATE CASCADE
                         ON DELETE CASCADE
                     )
            """;
            statement.execute(createRelationTable);

            ResultSet certificates = statement.executeQuery("""
                    SELECT uuid, source_certificate_uuid, issuer_dn_normalized, subject_dn_normalized, key_uuid
                        FROM certificate WHERE source_certificate_uuid IS NOT NULL
                    """);

            while (certificates.next()) {
                String relationType;
                try (final Statement selectSourceCertificate = context.getConnection().createStatement()) {
                    String sourceCertificateUuid = certificates.getString("source_certificate_uuid");
                    ResultSet sourceCertificate = selectSourceCertificate.executeQuery("""
                            SELECT issuer_dn_normalized, subject_dn_normalized, key_uuid
                                FROM certificate WHERE uuid = '%s'
                            """.formatted(sourceCertificateUuid));
                    if (sourceCertificate.next()) {
                        String issuerDnCertificate = certificates.getString("issuer_dn_normalized");
                        String issuerDnSourceCertificate = sourceCertificate.getString("issuer_dn_normalized");
                        String subjectDnCertificate = certificates.getString("subject_dn_normalized");
                        String subjectDnSourceCertificate = sourceCertificate.getString("subject_dn_normalized");
                        if ( (issuerDnCertificate != null && issuerDnSourceCertificate != null && subjectDnCertificate != null && subjectDnSourceCertificate != null) &&
                                (issuerDnCertificate.equals(issuerDnSourceCertificate) && subjectDnCertificate.equals(subjectDnSourceCertificate))) {
                            if (certificates.getString(KEY_UUID) != null && certificates.getString(KEY_UUID).equals(sourceCertificate.getString(KEY_UUID)))
                                relationType = CertificateRelationType.RENEWAL.name();
                            else relationType = CertificateRelationType.REKEY.name();
                        } else {
                            relationType = CertificateRelationType.REPLACEMENT.name();
                        }

                        preparedStatement.setObject(1, certificates.getObject("uuid"), Types.OTHER);
                        preparedStatement.setObject(2, sourceCertificateUuid, Types.OTHER);
                        preparedStatement.setObject(3, relationType);

                        preparedStatement.addBatch();
                    }
                }
            }
            preparedStatement.executeBatch();
            statement.execute("ALTER TABLE certificate DROP COLUMN source_certificate_uuid");
        }
    }
}

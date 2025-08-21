package db.migration;

import com.czertainly.api.model.core.certificate.CertificateRelationType;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.DatabaseMigration;
import org.bouncycastle.asn1.x509.Extension;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.Base64;

@SuppressWarnings("java:S101")
public class V202508130940__CertificateRelations extends BaseJavaMigration {

    public static final String KEY_UUID = "key_uuid";

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202508130940__CertificateRelations.getChecksum();
    }

    @Override
    public void migrate(Context context) throws Exception {

        migrateAltKeyFingerprint(context);
        String updateCertificateRelations = """
                INSERT INTO certificate_relation
                (successor_certificate_uuid, predecessor_certificate_uuid, relation_type, created_at)
                VALUES
                (?, ?, ?, NOW())
                """;
        try (final Statement statement = context.getConnection().createStatement();
             final PreparedStatement preparedStatement = context.getConnection().prepareStatement(updateCertificateRelations)) {
            String createRelationTable = """
                    CREATE TABLE certificate_relation (
                             successor_certificate_uuid UUID NOT NULL,
                             predecessor_certificate_uuid UUID NOT NULL,
                             relation_type TEXT,
                             created_at TIMESTAMP WITH TIME ZONE,
                    
                             CONSTRAINT pk_certificate_relation PRIMARY KEY (successor_certificate_uuid, predecessor_certificate_uuid),
                    
                             CONSTRAINT fk_certificate_relation_successor_certificate
                                 FOREIGN KEY (successor_certificate_uuid)
                                 REFERENCES certificate (uuid)
                                 ON UPDATE CASCADE
                                 ON DELETE CASCADE,
                    
                             CONSTRAINT fk_certificate_relation_predecessor_certificate
                                 FOREIGN KEY (predecessor_certificate_uuid)
                                 REFERENCES certificate (uuid)
                                 ON UPDATE CASCADE
                                 ON DELETE CASCADE
                             )
                    """;
            statement.execute(createRelationTable);

            ResultSet certificates = statement.executeQuery("""
                    SELECT uuid, source_certificate_uuid, issuer_dn_normalized, subject_dn_normalized, issuer_serial_number, key_uuid
                        FROM certificate WHERE source_certificate_uuid IS NOT NULL
                    """);

            while (certificates.next()) {
                String relationType;
                String sourceCertificateUuid = certificates.getString("source_certificate_uuid");
                try (final PreparedStatement selectSourceCertificate = context.getConnection().prepareStatement(
                        "SELECT issuer_dn_normalized, subject_dn_normalized, issuer_serial_number, key_uuid FROM certificate WHERE uuid = ?")) {
                    selectSourceCertificate.setObject(1, sourceCertificateUuid, Types.OTHER);
                    ResultSet sourceCertificate = selectSourceCertificate.executeQuery();
                    if (sourceCertificate.next()) {
                        String issuerDnCertificate = certificates.getString("issuer_dn_normalized");
                        String issuerDnSourceCertificate = sourceCertificate.getString("issuer_dn_normalized");
                        String subjectDnCertificate = certificates.getString("subject_dn_normalized");
                        String subjectDnSourceCertificate = sourceCertificate.getString("subject_dn_normalized");
                        String issuerSNCertificate = certificates.getString("issuer_serial_number");
                        String issuerSNSourceCertificate = sourceCertificate.getString("issuer_serial_number");

                        if ((issuerDnCertificate != null && issuerDnSourceCertificate != null && subjectDnCertificate != null && subjectDnSourceCertificate != null && issuerSNCertificate != null && issuerSNSourceCertificate != null) &&
                                (issuerDnCertificate.equals(issuerDnSourceCertificate) && subjectDnCertificate.equals(subjectDnSourceCertificate) && issuerSNCertificate.equals(issuerSNSourceCertificate))) {
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

    private void migrateAltKeyFingerprint(Context context) throws SQLException {
        String addAltKeyFingerprintColumn = "ALTER TABLE certificate ADD COLUMN alt_key_fingerprint TEXT";
        String updateAltKeyFingerprint = "UPDATE certificate SET alt_key_fingerprint = ? WHERE uuid = ?";
        try (final Statement statement = context.getConnection().createStatement();
             PreparedStatement preparedStatement = context.getConnection().prepareStatement(updateAltKeyFingerprint)) {
            statement.execute(addAltKeyFingerprintColumn);
            ResultSet certificates = statement.executeQuery("SELECT c.uuid, cc.content FROM certificate c JOIN certificate_content cc ON c.certificate_content_id = cc.id WHERE c.hybrid_certificate = TRUE");
            while (certificates.next()) {
                String content = certificates.getString("cc.content");
                String altPublicKeyFingerprint;
                try {
                    X509Certificate x509Certificate = CertificateUtil.getX509Certificate(content);
                    PublicKey altKey = CertificateUtil.getAltPublicKey(x509Certificate.getExtensionValue(Extension.subjectAltPublicKeyInfo.getId()));
                    altPublicKeyFingerprint = CertificateUtil.getThumbprint(Base64.getEncoder().encodeToString(altKey.getEncoded()).getBytes(StandardCharsets.UTF_8));
                } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | CertificateException e) {
                    continue;
                }
                preparedStatement.setString(1, altPublicKeyFingerprint);
                preparedStatement.setObject(2, certificates.getObject("c.uuid"), Types.OTHER);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        }
    }

}

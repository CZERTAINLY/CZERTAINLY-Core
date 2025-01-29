package db.migration;

import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.CryptographyUtil;
import com.czertainly.core.util.KeySizeUtil;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.sql.*;
import java.util.*;

public class V202501281511__LinkKeysToCertificates extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        prepareDbForUploadedKeys(context);
        linkKeysToCertificates(context);
    }

    private void prepareDbForUploadedKeys(Context context) throws SQLException {
        String alterCkTableQuery = "ALTER TABLE cryptographic_key ALTER COLUMN token_instance_uuid DROP NOT NULL;";
        String alterCkiTableQuery = "ALTER TABLE cryptographic_key_item ALTER COLUMN key_reference_uuid DROP NOT NULL;";
        String addPkColumnToCsrQuery = "ALTER TABLE CERTIFICATE_REQUEST ADD COLUMN public_key_uuid uuid;";
        try (final Statement statement = context.getConnection().createStatement()) {
            statement.execute(alterCkTableQuery);
            statement.executeQuery(alterCkiTableQuery);
            statement.executeQuery(addPkColumnToCsrQuery);
        }
    }

    private void linkKeysToCertificates(Context context) throws SQLException, CertificateException, NoSuchAlgorithmException {
        String updatePublicKeyQuery = "UPDATE certificate SET key_uuid = ? WHERE uuid = ?";
        String createCkQuery = """
                INSERT INTO cryptographic_key (
                    uuid,
                    i_author,
                    i_cre,
                    i_upd,
                    name
                ) VALUES (
                    ?,
                    ?,
                    NOW(),
                    NOW(),
                    ?
                );
                """;
        String createCkiQuery = """
                INSERT INTO cryptographic_key_item (
                    uuid,
                    name,
                    type,
                    cryptographic_key_uuid,
                    cryptographic_algorithm,
                    format,
                    key_data,
                    state,
                    enabled,
                    length,
                    fingerprint,
                    created_at,
                    updated_at
                ) VALUES (
                    ?,
                    ?,
                    'PUBLIC_KEY',
                    ?,
                    ?,
                    ?,
                    ?,
                    'ACTIVE',
                    TRUE,
                    ?,
                    ?,
                    NOW(),
                    NOW()
                );
                
                """;
        Map<String, String> fingeprintToKeyUuidMap = getFingerprinToKeyUuidMap(context);
        try (final Statement select = context.getConnection().createStatement();
             final PreparedStatement updatePublicKeyPs = context.getConnection().prepareStatement(updatePublicKeyQuery);
             final PreparedStatement insertCkPs = context.getConnection().prepareStatement(createCkQuery);
             final PreparedStatement insertCkiPs = context.getConnection().prepareStatement(createCkiQuery)) {
            ResultSet certificatesWithoutKeyLink = select.executeQuery("""
                                            SELECT
                                            c.common_name,
                                            c.i_author,
                                            c.uuid AS certificate_uuid,
                                            cc.content AS certificate_content
                                            FROM certificate c
                                            INNER JOIN certificate_content cc
                                            ON c.certificate_content_id = cc.id
                    """);
            while (certificatesWithoutKeyLink.next()) {
                String certificateUuid = certificatesWithoutKeyLink.getString("certificate_uuid");
                PublicKey publicKey = CertificateUtil.parseCertificate(certificatesWithoutKeyLink.getString("certificate_content")).getPublicKey();
                if (publicKey != null) {
                    UUID ckUuid;
                    String publicKeyFingerprint = CertificateUtil.getThumbprint(Base64.getEncoder().encodeToString(publicKey.getEncoded()).getBytes(StandardCharsets.UTF_8));
                    if (fingeprintToKeyUuidMap.get(publicKeyFingerprint) != null) {
                        ckUuid = UUID.fromString(fingeprintToKeyUuidMap.get(publicKeyFingerprint));
                    } else {
                        ckUuid = UUID.randomUUID();
                        String keyName = "certKey_" + certificatesWithoutKeyLink.getString("common_name");

                        insertCkPs.setObject(1, ckUuid, Types.OTHER);
                        insertCkPs.setString(2, certificatesWithoutKeyLink.getString("i_author"));
                        insertCkPs.setString(3, keyName);
                        insertCkPs.addBatch();

                        insertCkiPs.setObject(1, UUID.randomUUID(), Types.OTHER);
                        insertCkiPs.setString(2, keyName);
                        insertCkiPs.setObject(3, ckUuid, Types.OTHER);
                        insertCkiPs.setString(4, CertificateUtil.getAlgorithmFromProviderName(publicKey.getAlgorithm()));
                        insertCkiPs.setString(5, CryptographyUtil.getPublicKeyFormat(publicKey.getEncoded()).toString());
                        insertCkiPs.setString(5, Base64.getEncoder().encodeToString(publicKey.getEncoded()));
                        insertCkiPs.setInt(6, KeySizeUtil.getKeyLength(publicKey));
                        insertCkiPs.setString(7, publicKeyFingerprint);
                        insertCkiPs.addBatch();

                        fingeprintToKeyUuidMap.put(publicKeyFingerprint, String.valueOf(ckUuid));
                    }

                    updatePublicKeyPs.setObject(1, ckUuid, Types.OTHER);
                    updatePublicKeyPs.setObject(2, certificateUuid, Types.OTHER);
                    updatePublicKeyPs.addBatch();
                }
            }
            insertCkPs.executeBatch();
            insertCkiPs.executeBatch();
        }
    }

    private Map<String, String> getFingerprinToKeyUuidMap(Context context) throws SQLException {
        Map<String, String> valueMap = new HashMap<>();
        String query = """
                SELECT ck.uuid,
                cki.fingerprint
                FROM cryptographic_key ck
                INNER JOIN cryptographic_key_item cki
                ON ck.uuid = cki.cryptographic_key_uuid
                """;
        try (final Statement select = context.getConnection().createStatement()) {
            ResultSet resultSet = select.executeQuery(query);
            while (resultSet.next()) {
                String fingerprint = resultSet.getString("cki.fingerprint");
                String uuid = resultSet.getString("cki.uuid");
                valueMap.put(fingerprint, uuid);
            }
        }
        return valueMap;
    }
}

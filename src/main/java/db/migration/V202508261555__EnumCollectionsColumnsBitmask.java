package db.migration;

import com.czertainly.api.model.common.enums.BitMaskEnum;
import com.czertainly.api.model.core.certificate.CertificateKeyUsage;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.core.util.MetaDefinitions;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("java:S101")
public class V202508261555__EnumCollectionsColumnsBitmask extends BaseJavaMigration {

    private static final Map<Integer, KeyUsage> oldBitToKeyUsage = Map.of(
            1, KeyUsage.SIGN,
            2, KeyUsage.VERIFY,
            4, KeyUsage.ENCRYPT,
            8, KeyUsage.DECRYPT,
            10, KeyUsage.WRAP,
            20, KeyUsage.UNWRAP
    );

    @Override
    public void migrate(Context context) throws Exception {
        migrateColumnToBitmask(context, "certificate", "key_usage",
                raw -> MetaDefinitions.deserializeArrayString(raw).stream()
                .map(CertificateKeyUsage::fromCode)
                .collect(Collectors.toSet())
               );
        migrateColumnToBitmask(
                context,
                "cryptographic_key_item",
                "usage",
                V202508261555__EnumCollectionsColumnsBitmask::listOfIntEnumRepresentationToBitMask
        );
        migrateColumnToBitmask(
                context,
                "token_profile",
                "usage",
                V202508261555__EnumCollectionsColumnsBitmask::listOfIntEnumRepresentationToBitMask
        );
    }

    private static Set<KeyUsage> listOfIntEnumRepresentationToBitMask(String raw) {
        return Arrays.stream(raw.split(","))
                .map(Integer::parseInt)
                .map(oldBitToKeyUsage::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private <E extends Enum<E> & BitMaskEnum> void migrateColumnToBitmask(
            Context context,
            String tableName,
            String columnName,
            Function<String, Set<E>> parser
    ) throws SQLException {

        Map<UUID, Integer> updates = new HashMap<>();

        try (Statement selectStatement = context.getConnection().createStatement()) {
            String selectSql = String.format("SELECT uuid, %s FROM %s", columnName, tableName);
            ResultSet rs = selectStatement.executeQuery(selectSql);

            while (rs.next()) {
                String rawValue = rs.getString(columnName);
                int bitmask;
                if (rawValue == null) bitmask = 0;
                else {
                    Set<E> values = parser.apply(rawValue);
                    bitmask = BitMaskEnum.convertListToBitMask(values);
                }
                updates.put((UUID) rs.getObject("uuid"), bitmask);

            }

            selectStatement.execute(String.format("ALTER TABLE %s DROP COLUMN %s", tableName, columnName));
            selectStatement.execute(String.format("ALTER TABLE %s ADD COLUMN %s INTEGER", tableName, columnName));
        }

        String updateSql = String.format("UPDATE %s SET %s = ? WHERE uuid = ?", tableName, columnName);
        try (PreparedStatement ps = context.getConnection().prepareStatement(updateSql)) {
            for (Map.Entry<UUID, Integer> entry : updates.entrySet()) {
                ps.setInt(1, entry.getValue());                // bitmask
                ps.setObject(2, entry.getKey(), Types.OTHER);  // uuid
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }


}

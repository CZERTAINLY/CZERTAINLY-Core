package db.migration;

import com.czertainly.api.model.core.oid.SystemOid;
import com.czertainly.core.util.MetaDefinitions;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

@SuppressWarnings("java:S101")
public class V202507231633__TranslateExtendedKeyUsage extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (final Statement select = context.getConnection().createStatement()) {
            ResultSet certificates = select.executeQuery("SELECT uuid, extended_key_usage FROM certificate");
            String updateExtendedKeyUsage = "UPDATE certificate SET extended_key_usage = ? WHERE uuid = ?";
            try (PreparedStatement preparedStatement = context.getConnection().prepareStatement(updateExtendedKeyUsage);) {
                while (certificates.next()) {
                    String extendedKeyUsage = certificates.getString("extended_key_usage");
                    if (extendedKeyUsage != null && !extendedKeyUsage.equals("null") ) {
                        List<String> extendedKeyUsagesOid = MetaDefinitions.deserializeArrayString(extendedKeyUsage);
                        String extendedKeyUsagesName = MetaDefinitions.serializeArrayString(extendedKeyUsagesOid.stream().map(oid -> {
                            SystemOid systemOid = SystemOid.fromOID(oid);
                            if (systemOid == null) return oid;
                            return systemOid.getDisplayName();
                        }).toList());
                        preparedStatement.setString(1, extendedKeyUsagesName);
                        preparedStatement.setObject(2, certificates.getObject("uuid"), Types.OTHER);
                        preparedStatement.addBatch();
                    }
                }
                preparedStatement.executeBatch();
            }
        }
    }
}

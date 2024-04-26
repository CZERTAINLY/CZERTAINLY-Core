package com.czertainly.core.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Simple tests for calculating checksums and validating the migration scripts integrity.
 */
public class DatabaseMigrationTest {

    @Test
    public void testCalculateChecksum_V202206151000__AttributeChanges() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202206151000__AttributeChanges.java");

        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202206151000__AttributeChanges.getChecksum(), checksum);
    }

    @Test
    public void testCalculateChecksum_V202209211100__Access_Control() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202209211100__Access_Control.java");

        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202209211100__Access_Control.getChecksum(), checksum);
    }

    @Test
    public void testCalculateChecksum_V202211031400__AttributeV2Changes() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202211031400__AttributeV2Changes.java");

        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202211031400__AttributeV2Changes.getChecksum(), checksum);
    }

    @Test
    public void testCalculateChecksum_V202211141030__AttributeV2TablesAndMigration() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202211141030__AttributeV2TablesAndMigration.java");

        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202211141030__AttributeV2TablesAndMigration.getChecksum(), checksum);
    }

    @Test
    public void testCalculateChecksum_V202301311500__PublicKeyMigration() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202301311500__PublicKeyMigration.java");

        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202301311500__PublicKeyMigration.getChecksum(), checksum);
    }

    @Test
    public void testCalculateChecksum_V202303211718__Scep_Roles() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202303211718__Scep_Roles.java");

        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202303211718__Scep_Roles.getChecksum(), checksum);
    }

    @Test
    public void testCalculateChecksum_V202308050825__UpdateAcmeScepRolesPermissions() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202308050825__UpdateAcmeScepRolesPermissions.java");

        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202308050825__UpdateAcmeScepRolesPermissions.getChecksum(), checksum);
    }

    @Test
    public void testCalculateChecksum_V202402171510__UpdateAndOptimizeAttributesModelMigration() throws IOException {
        int checksum = DatabaseMigration.calculateChecksum("src/main/java/db/migration/V202402171510__UpdateAndOptimizeAttributesModelMigration.java");
        Assertions.assertEquals(DatabaseMigration.JavaMigrationChecksums.V202402171510__UpdateAndOptimizeAttributesModelMigration.getChecksum(), checksum);
    }
}

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
}

package com.czertainly.core.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * Helper class for calculating checksums of files.
 * And for storing the checksums of the Java-based migrations.
 */
public class DatabaseMigration {

    /**
     * Calculates the checksum of a file.
     *
     * @param filePath The path to the file.
     * @return The checksum of the file.
     * @throws IOException when the file could not be read.
     */
    public static int calculateChecksum(String filePath) throws IOException {
        FileInputStream fis = new java.io.FileInputStream(filePath);
        CRC32 crc32 = new java.util.zip.CRC32();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = fis.read(buffer)) != -1) {
            crc32.update(buffer, 0, count);
        }
        fis.close();
        return (int) crc32.getValue();
    }

    /**
     * Stores the checksum of a Java-based migration.
     */
    public enum JavaMigrationChecksums {
        V202206151000__AttributeChanges(-225727414);
        private final int checksum;

        JavaMigrationChecksums(int checksum) {
            this.checksum = checksum;
        }

        public int getChecksum() {
            return checksum;
        }
    }
}

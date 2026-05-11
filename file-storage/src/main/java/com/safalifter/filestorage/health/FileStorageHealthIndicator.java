package com.safalifter.filestorage.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class FileStorageHealthIndicator implements HealthIndicator {

    @Value("${file.storage.path}")
    private String storagePath;

    private static final String TEST_FILE_PREFIX = "health-check-";
    private static final String TEST_FILE_SUFFIX = ".tmp";

    @Override
    public Health health() {
        try {
            Path storageDir = Paths.get(storagePath);
            File storageFolder = storageDir.toFile();

            if (!storageFolder.exists()) {
                return Health.down()
                        .withDetail("path", storagePath)
                        .withDetail("status", "directory_not_found")
                        .withDetail("error", "Storage directory does not exist")
                        .build();
            }

            if (!storageFolder.isDirectory()) {
                return Health.down()
                        .withDetail("path", storagePath)
                        .withDetail("status", "not_a_directory")
                        .withDetail("error", "Path is not a directory")
                        .build();
            }

            if (!storageFolder.canRead()) {
                return Health.down()
                        .withDetail("path", storagePath)
                        .withDetail("status", "no_read_permission")
                        .withDetail("error", "Cannot read from storage directory")
                        .build();
            }

            if (!storageFolder.canWrite()) {
                return Health.down()
                        .withDetail("path", storagePath)
                        .withDetail("status", "no_write_permission")
                        .withDetail("error", "Cannot write to storage directory")
                        .build();
            }

            testReadWrite(storageDir);

            long freeSpace = storageFolder.getFreeSpace();
            long totalSpace = storageFolder.getTotalSpace();
            double usedPercent = (double) (totalSpace - freeSpace) / totalSpace * 100;

            return Health.up()
                    .withDetail("path", storagePath)
                    .withDetail("status", "healthy")
                    .withDetail("freeSpaceMB", formatSize(freeSpace))
                    .withDetail("totalSpaceMB", formatSize(totalSpace))
                    .withDetail("usedPercent", String.format("%.2f%%", usedPercent))
                    .build();

        } catch (Exception e) {
            log.error("File storage health check failed", e);
            return Health.down()
                    .withDetail("path", storagePath)
                    .withDetail("status", "error")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private void testReadWrite(Path storageDir) throws IOException {
        Path testFile = Files.createTempFile(storageDir, TEST_FILE_PREFIX, TEST_FILE_SUFFIX);
        try {
            String testContent = "health-check-test-content";
            Files.writeString(testFile, testContent);
            String readContent = Files.readString(testFile);
            if (!testContent.equals(readContent)) {
                throw new IOException("File content mismatch during read-write test");
            }
        } finally {
            try {
                Files.deleteIfExists(testFile);
            } catch (IOException e) {
                log.warn("Failed to delete test file: {}", testFile, e);
            }
        }
    }

    private String formatSize(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}

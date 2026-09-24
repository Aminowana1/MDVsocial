package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CoalescingFileWriterTest {
    @TempDir Path directory;
    @Test void shutdownPersistsLastSnapshotAndLeavesNoTemporaryFiles() throws Exception {
        Path file = directory.resolve("homes-lock-data.yml");
        Files.writeString(file, "previous");
        try (CoalescingFileWriter writer = new CoalescingFileWriter(file)) {
            for (int i = 0; i < 1000; i++) writer.save("version: " + i);
        }
        assertEquals("version: 999", Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void closeReportsWriteFailure() {
        CoalescingFileWriter writer = new CoalescingFileWriter(directory.resolve("missing/lock.yml"));
        writer.save("must not disappear silently");
        assertThrows(IllegalStateException.class, writer::close);
    }
}

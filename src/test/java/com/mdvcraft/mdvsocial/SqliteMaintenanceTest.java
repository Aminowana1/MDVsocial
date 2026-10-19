package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMaintenanceTest {

    @TempDir
    File dir;

    private Connection openWal(File file) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("CREATE TABLE IF NOT EXISTS titles(uuid TEXT, title TEXT)");
        }
        return connection;
    }

    private static int count(File file) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM titles")) {
            return rs.getInt(1);
        }
    }

    @Test
    void checkpointEmptiesTheWal() throws Exception {
        File db = new File(dir, "player-data.db");
        File wal = new File(dir, "player-data.db-wal");
        try (Connection c = openWal(db); Statement st = c.createStatement()) {
            for (int i = 0; i < 50; i++)
                st.execute("INSERT INTO titles VALUES ('p" + i + "', 'noble')");
            assertTrue(wal.length() > 0, "writes stay in the -wal before a checkpoint");
            SqliteMaintenance.checkpoint(c);
            assertEquals(0, wal.length());
        }
    }

    @Test
    void backupIsCompleteWithoutTheWal() throws Exception {
        File db = new File(dir, "player-data.db");
        File copy = new File(new File(dir, "backups/20261018-210000"), "player-data.db");
        try (Connection c = openWal(db)) {
            try (Statement st = c.createStatement()) {
                st.execute("INSERT INTO titles VALUES ('a', 'heroe'), ('b', 'noble')");
            }
            SqliteMaintenance.backup(c, copy);
            assertThrows(Exception.class, () -> SqliteMaintenance.backup(c, copy), "never overwrites a backup");
        }
        assertFalse(new File(copy.getPath() + "-wal").exists());
        assertEquals(2, count(copy));
    }

    @Test
    void keepsOnlyTheNewestBackups() {
        List<File> folders = List.of(new File("20261018-120000"), new File("20261016-090000"),
                new File("20261017-080000"));
        assertEquals(List.of(new File("20261016-090000")), DatabaseMaintenance.expired(folders, 2));
        assertEquals(List.of(), DatabaseMaintenance.expired(folders, 3));
        assertEquals(List.of(), DatabaseMaintenance.expired(folders, 0));
    }
}

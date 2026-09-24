package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageOptimizationTest {
    @TempDir Path directory;
    private final Logger log = Logger.getLogger("StorageTest");

    private MailSqliteStore mail(int capacity) { return new MailSqliteStore(directory.resolve("mail.db").toFile(), log, capacity); }
    private JavaPlugin plugin(int capacity) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("performance.profile-cache-size", capacity);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        return plugin;
    }

    @Test void tenThousandExistingMailboxesLoadOnDemandWithBoundedMemory() throws Exception {
        try (MailSqliteStore store = mail(64)) { store.open(null); }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("mail.db"))) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mail_sections VALUES(?,?)")) {
                for (int i = 0; i < 10_000; i++) {
                    ps.setString(1, "mailbox." + new UUID(0, i));
                    ps.setString(2, "letters:\n  a:\n    message: Mensaje existente\n    read: false\nblocked: [abc]\nsystem:\n  welcome-delivered:\n    bienvenida-v1: true\n");
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        }
        try (MailSqliteStore store = mail(64)) {
            var data = store.open(null);
            assertEquals(0, data.residentCount());
            assertEquals(10_000, data.mailboxIds().size());
            assertEquals(0, data.residentCount(), "enumeration must not load mailbox contents");
            for (int i = 0; i < 10_000; i++) {
                String key = "mailbox." + new UUID(0, i);
                assertEquals("Mensaje existente", data.getString(key + ".letters.a.message", ""));
                assertEquals(List.of("abc"), data.getStringList(key + ".blocked"));
                assertTrue(data.getBoolean(key + ".system.welcome-delivered.bienvenida-v1", false));
                assertTrue(data.residentCount() <= 64);
            }
            System.out.println("MAIL SCALE: 10000 existing mailboxes; 0 loaded at open; max 64 resident; all content preserved.");
        }
    }

    @Test void dirtyEvictionRapidWritesAndReopenKeepLatestValues() throws Exception {
        try (MailSqliteStore store = mail(2)) {
            var data = store.open(null);
            for (int round = 0; round < 10; round++) for (int i = 0; i < 100; i++) {
                data.set("mailbox." + i + ".letters.a.message", "round-" + round);
                data.set("mailbox." + i + ".letters.a.read", true);
                assertTrue(data.residentCount() <= 2);
            }
            assertEquals("round-9", data.getString("mailbox.0.letters.a.message", ""));
            // close must also flush changes that did not reach an explicit save call.
        }
        try (MailSqliteStore store = mail(2)) {
            var data = store.open(null);
            assertEquals(100, data.mailboxIds().size());
            for (int i = 0; i < 100; i++) {
                assertEquals("round-9", data.getString("mailbox." + i + ".letters.a.message", ""));
                assertTrue(data.getBoolean("mailbox." + i + ".letters.a.read", false));
            }
        }
    }

    @Test void deletionAndImmediateRecreationNeverResurrectOldLetters() throws Exception {
        try (MailSqliteStore store = mail(1)) {
            var data = store.open(null);
            data.set("mailbox.a.letters.old.message", "old"); store.flush(data);
            data.set("mailbox.a", null);
            data.set("mailbox.a.letters.new.message", "new");
            data.set("mailbox.b.letters.other.message", "other");
            assertFalse(data.contains("mailbox.a.letters.old"));
            assertEquals("new", data.getString("mailbox.a.letters.new.message", ""));
            data.set("broadcasts.deleted.message", "campaign");
            data.set("broadcasts.deleted", null);
        }
        try (MailSqliteStore store = mail(1)) {
            var data = store.open(null);
            assertFalse(data.contains("mailbox.a.letters.old"));
            assertTrue(data.contains("mailbox.a.letters.new"));
            assertFalse(data.contains("broadcasts.deleted.message"));
        }
    }

    @Test void legacyMailMigrationPreservesUnknownFieldsAndIsNotRepeated() throws Exception {
        Path legacy = directory.resolve("legacy.yml");
        Files.writeString(legacy, "mailbox:\n  abc:\n    letters:\n      a:\n        message: Hola\n        clan-banner: preserved\n    blocked: [other]\nbroadcasts:\n  campaign:\n    recipients: 3000\n");
        try (MailSqliteStore store = mail(1)) {
            var data = store.open(legacy.toFile());
            assertEquals("preserved", data.getString("mailbox.abc.letters.a.clan-banner", ""));
            assertEquals(3000, data.getLong("broadcasts.campaign.recipients", 0));
            data.set("mailbox.abc.letters.a.message", "changed");
        }
        try (MailSqliteStore store = mail(1)) {
            assertEquals("changed", store.open(legacy.toFile()).getString("mailbox.abc.letters.a.message", ""));
        }
        assertTrue(Files.exists(legacy));
    }

    @Test void malformedLegacyMailDoesNotMarkMigrationComplete() throws Exception {
        Path legacy = directory.resolve("broken.yml");
        Files.writeString(legacy, "mailbox: [unterminated");
        MailSqliteStore store = mail(1);
        try { assertThrows(Exception.class, () -> store.open(legacy.toFile())); }
        finally { store.close(); }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("mail.db"));
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM mail_meta")) {
            assertTrue(rs.next()); assertEquals(0, rs.getInt(1));
        }
    }

    @Test void fiveThousandProfilesReuseReadsAndEvictOnQuit() throws Exception {
        JavaPlugin plugin = plugin(8192);
        try (PlayerDataStore store = new PlayerDataStore(plugin, directory.resolve("players.db").toFile())) {
            store.open(null);
            for (int i = 0; i < 5000; i++) store.getString("players." + new UUID(0, i) + ".active", "");
            long loads = store.profileLoads();
            for (int round = 0; round < 10; round++) for (int i = 0; i < 5000; i++) {
                String key = "players." + new UUID(0, i);
                store.getString(key + ".active", "");
                store.getBoolean(key + ".punishment.active", false);
                store.getStringList(key + ".unlocked");
            }
            assertEquals(5000, loads);
            assertEquals(loads, store.profileLoads());
            for (int i = 0; i < 5000; i++) store.forget(new UUID(0, i));
            assertEquals(0, store.residentCount());
            System.out.println("PROFILE SCALE: 5000 profiles, 150000 subsequent field reads, 0 additional profile loads; quit releases all.");
        }
    }

    @Test void profileEvictionAndReopenPreserveTitlesPunishmentsAndLists() throws Exception {
        JavaPlugin plugin = plugin(2);
        Path database = directory.resolve("players.db");
        String key = "players." + new UUID(0, 1);
        try (PlayerDataStore store = new PlayerDataStore(plugin, database.toFile())) {
            store.open(null);
            store.set(key + ".active", "vip"); store.set(key + ".last-name", "Tester");
            store.set(key + ".unlocked", List.of("VIP", "vip", "Mega Rank"));
            store.set(key + ".punishment.active", true);
            store.set(key + ".punishment.title", "castigo");
            store.set(key + ".punishment.previous-title", "vip");
            List<String> list = store.getStringList(key + ".unlocked"); list.clear();
            assertEquals(List.of("mega_rank", "vip"), store.getStringList(key + ".unlocked"));
            for (int i = 2; i < 1000; i++) store.getString("players." + new UUID(0, i) + ".active", "");
            assertEquals(2, store.residentCount());
            assertEquals("vip", store.getString(key + ".active", ""));
        }
        try (PlayerDataStore store = new PlayerDataStore(plugin, database.toFile())) {
            store.open(null);
            assertEquals("Tester", store.getString(key + ".last-name", ""));
            assertEquals(List.of("mega_rank", "vip"), store.getStringList(key + ".unlocked"));
            assertTrue(store.getBoolean(key + ".punishment.active", false));
            assertEquals("castigo", store.getString(key + ".punishment.title", ""));
            assertEquals("vip", store.getString(key + ".punishment.previous-title", ""));
            store.set(key + ".punishment", null);
            assertFalse(store.getBoolean(key + ".punishment.active", true));
            assertEquals("", store.getString(key + ".punishment.previous-title", "not-empty"));
        }
    }
}

package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Per-mailbox SQLite persistence with a fast main-thread YAML-compatible cache.
 * Each mutation only flushes affected mailboxes/campaigns, never the whole mail store.
 * JDBC writes execute serially off the Minecraft server thread.
 */
final class MailSqliteStore implements AutoCloseable {
    static final class TrackedMail extends YamlConfiguration {
        private final Set<String> dirty = new HashSet<>();
        private boolean tracking = true;
        @Override public void set(String path, Object value) {
            super.set(path, value);
            if (tracking && path != null) {
                String[] split = path.split("\\.", 3);
                if (split.length >= 2 && (split[0].equals("mailbox") || split[0].equals("broadcasts")))
                    dirty.add(split[0] + "." + split[1]);
            }
        }
        Set<String> drain() {
            Set<String> snapshot = new HashSet<>(dirty);
            dirty.clear();
            return snapshot;
        }
        void importSection(String key, String yaml) throws Exception {
            YamlConfiguration fragment = new YamlConfiguration();
            fragment.loadFromString(yaml);
            tracking = false;
            try {
                for (String path : fragment.getKeys(true)) {
                    if (!fragment.isConfigurationSection(path))
                        set(key + "." + path, fragment.get(path));
                }
            } finally { tracking = true; }
        }
        String fragment(String key) {
            ConfigurationSection section = getConfigurationSection(key);
            if (section == null) return null;
            YamlConfiguration fragment = new YamlConfiguration();
            for (String path : section.getKeys(true)) {
                if (!section.isConfigurationSection(path)) fragment.set(path, section.get(path));
            }
            return fragment.saveToString();
        }
    }

    private final File dbFile;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MDVSocial-Mail-SQLite"); t.setDaemon(true); return t;
    });
    private Connection connection;
    private Future<?> pending;
    private volatile boolean failed;

    MailSqliteStore(File dbFile, Logger logger) { this.dbFile = dbFile; this.logger = logger; }

    TrackedMail open(File legacyFile) throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS mail_sections(section_key TEXT PRIMARY KEY, yaml TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS mail_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        TrackedMail cache = new TrackedMail();
        boolean migrated;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT 1 FROM mail_meta WHERE key='initialized'")) {
            migrated = rs.next();
        }
        if (!migrated && legacyFile.isFile() && legacyFile.length() > 0) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(legacyFile);
            // Import only after a successful parse. Leave the original file unchanged as backup.
            for (String parent : List.of("mailbox", "broadcasts")) {
                ConfigurationSection section = old.getConfigurationSection(parent);
                if (section == null) continue;
                for (String id : section.getKeys(false)) {
                    String key = parent + "." + id;
                    YamlConfiguration fragment = new YamlConfiguration();
                    ConfigurationSection source = old.getConfigurationSection(key);
                    if (source == null) continue;
                    for (String path : source.getKeys(true))
                        if (!source.isConfigurationSection(path)) fragment.set(path, source.get(path));
                    cache.importSection(key, fragment.saveToString());
                }
            }
            saveAll(cache);
            try (Statement st = connection.createStatement()) {
                st.execute("INSERT OR REPLACE INTO mail_meta(key,value) VALUES('initialized','1')");
            }
            logger.info("Correo migrado a mail-data.db; mail-data.yml se conserva intacto como respaldo.");
        } else {
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT section_key,yaml FROM mail_sections")) {
                while (rs.next()) cache.importSection(rs.getString(1), rs.getString(2));
            }
            if (!migrated) try (Statement st = connection.createStatement()) {
                st.execute("INSERT OR REPLACE INTO mail_meta(key,value) VALUES('initialized','1')");
            }
        }
        return cache;
    }

    private void saveAll(TrackedMail cache) throws Exception {
        Map<String,String> sections = new HashMap<>();
        for (String parent : List.of("mailbox", "broadcasts")) {
            ConfigurationSection section = cache.getConfigurationSection(parent);
            if (section != null) for (String id : section.getKeys(false)) {
                String key = parent + "." + id;
                sections.put(key, cache.fragment(key));
            }
        }
        persist(sections);
        cache.drain();
    }

    void flush(TrackedMail cache) {
        if (failed) throw new IllegalStateException("SQLite mail writer failed; refusing to discard mail changes");
        Set<String> dirty = cache.drain();
        if (dirty.isEmpty()) return;
        Map<String,String> updates = new HashMap<>();
        for (String key : dirty) updates.put(key, cache.fragment(key));
        pending = writer.submit(() -> {
            try { persist(updates); }
            catch (Exception e) { failed = true; logger.severe("No se pudo persistir correo SQLite: " + e); }
        });
    }

    private void persist(Map<String,String> updates) throws Exception {
        synchronized (this) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO mail_sections(section_key,yaml) VALUES(?,?) ON CONFLICT(section_key) DO UPDATE SET yaml=excluded.yaml");
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM mail_sections WHERE section_key=?")) {
                for (Map.Entry<String,String> e : updates.entrySet()) {
                    if (e.getValue() == null) { delete.setString(1, e.getKey()); delete.addBatch(); }
                    else { upsert.setString(1,e.getKey()); upsert.setString(2,e.getValue()); upsert.addBatch(); }
                }
                upsert.executeBatch(); delete.executeBatch(); connection.commit();
            } catch (Exception e) { connection.rollback(); throw e; }
            finally { connection.setAutoCommit(true); }
        }
    }

    @Override public void close() throws Exception {
        writer.shutdown();
        if (!writer.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("SQLite mail writer timeout");
        if (connection != null) connection.close();
        if (failed) throw new IllegalStateException("Some mail changes could not be written to SQLite");
    }
}

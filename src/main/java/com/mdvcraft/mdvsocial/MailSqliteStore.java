package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Lazy, bounded mailbox cache. Only the writer owns its SQLite connection after open(). */
final class MailSqliteStore implements AutoCloseable {
    final class TrackedMail {
        private final LinkedHashMap<String, YamlConfiguration> cache = new LinkedHashMap<>(16, .75f, true);
        private final Set<String> dirty = new HashSet<>();
        private String[] split(String path) {
            String[] parts = path.split("\\.", 3);
            if (parts.length < 2 || !(parts[0].equals("mailbox") || parts[0].equals("broadcasts")))
                throw new IllegalArgumentException("Use mailboxIds() for global mailbox enumeration: " + path);
            return new String[]{parts[0] + "." + parts[1], parts.length == 3 ? parts[2] : ""};
        }
        private YamlConfiguration section(String key) {
            YamlConfiguration section = cache.get(key);
            if (section != null) return section;
            while (cache.size() >= capacity) {
                String oldest = cache.keySet().iterator().next();
                flushKey(oldest);
                cache.remove(oldest);
            }
            try {
                String text = read(key);
                section = new YamlConfiguration();
                if (text != null) section.loadFromString(text);
            } catch (Exception e) { throw new IllegalStateException("Cannot load mailbox " + key, e); }
            cache.put(key, section);
            return section;
        }
        String getString(String path, String fallback) { String[] p = split(path); return section(p[0]).getString(p[1], fallback); }
        long getLong(String path, long fallback) { String[] p = split(path); return section(p[0]).getLong(p[1], fallback); }
        boolean getBoolean(String path, boolean fallback) { String[] p = split(path); return section(p[0]).getBoolean(p[1], fallback); }
        List<String> getStringList(String path) { String[] p = split(path); return section(p[0]).getStringList(p[1]); }
        boolean contains(String path) { String[] p = split(path); return section(p[0]).contains(p[1]); }
        ConfigurationSection getConfigurationSection(String path) {
            String[] p = split(path);
            YamlConfiguration section = section(p[0]);
            return p[1].isEmpty() ? section : section.getConfigurationSection(p[1]);
        }
        void set(String path, Object value) {
            String[] p = split(path);
            if (p[1].isEmpty()) {
                if (value != null) throw new IllegalArgumentException("Root replacement unsupported");
                enqueue(p[0], null);
                dirty.remove(p[0]); cache.remove(p[0]);
            } else {
                section(p[0]).set(p[1], value);
                dirty.add(p[0]);
            }
        }
        private void flushKey(String key) {
            if (!dirty.contains(key)) return;
            YamlConfiguration section = cache.get(key);
            enqueue(key, section.getKeys(false).isEmpty() ? null : section.saveToString());
            dirty.remove(key); // Only release ownership once the writer accepted the snapshot.
        }
        void flush() { for (String key : List.copyOf(dirty)) flushKey(key); }
        Set<String> mailboxIds() {
            Set<String> keys = new LinkedHashSet<>();
            try (PreparedStatement ps = reader.prepareStatement("SELECT section_key FROM mail_sections WHERE section_key LIKE 'mailbox.%'"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) keys.add(rs.getString(1));
            } catch (SQLException e) { throw new IllegalStateException("Cannot enumerate mailboxes", e); }
            synchronized (pending) {
                pending.forEach((key, update) -> { if (key.startsWith("mailbox.")) { if (update.yaml == null) keys.remove(key); else keys.add(key); } });
            }
            for (String key : cache.keySet()) if (key.startsWith("mailbox.")) keys.add(key);
            Set<String> ids = new LinkedHashSet<>();
            for (String key : keys) ids.add(key.substring(8));
            return ids;
        }
        int residentCount() { return cache.size(); }
    }

    private record Update(String yaml) {}
    private final File file;
    private final Logger logger;
    private final int capacity;
    private final LinkedHashMap<String, Update> pending = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "MDVSocial-Mail-SQLite"); t.setDaemon(true); return t; });
    private Connection connection, reader;
    private boolean running, closing;
    private Throwable failure;
    private TrackedMail cache;
    private static final int MAX_PENDING = 256;

    MailSqliteStore(File file, Logger logger) { this(file, logger, 128); }
    MailSqliteStore(File file, Logger logger, int capacity) {
        this.file = file; this.logger = logger; this.capacity = Math.max(1, capacity);
    }
    TrackedMail open(File legacy) throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL"); st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS mail_sections(section_key TEXT PRIMARY KEY, yaml TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS mail_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        boolean initialized;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT 1 FROM mail_meta WHERE key='initialized'")) { initialized = rs.next(); }
        if (!initialized) {
            // Strict parsing and one transaction: malformed legacy input never marks migration as complete.
            YamlConfiguration old = new YamlConfiguration();
            if (legacy != null && legacy.isFile() && legacy.length() > 0) old.load(legacy);
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT OR REPLACE INTO mail_sections(section_key,yaml) VALUES(?,?)")) {
                for (String parent : List.of("mailbox", "broadcasts")) {
                    ConfigurationSection root = old.getConfigurationSection(parent);
                    if (root == null) continue;
                    for (String id : root.getKeys(false)) {
                        ConfigurationSection source = root.getConfigurationSection(id);
                        if (source == null) continue;
                        YamlConfiguration fragment = new YamlConfiguration();
                        for (String path : source.getKeys(true)) if (!source.isConfigurationSection(path)) fragment.set(path, source.get(path));
                        insert.setString(1, parent + "." + id); insert.setString(2, fragment.saveToString()); insert.executeUpdate();
                    }
                }
                try (Statement st = connection.createStatement()) { st.execute("INSERT INTO mail_meta(key,value) VALUES('initialized','1')"); }
                connection.commit();
            } catch (Exception e) { connection.rollback(); throw e; }
            finally { connection.setAutoCommit(true); }
        }
        reader = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = reader.createStatement()) { st.execute("PRAGMA busy_timeout=5000"); st.execute("PRAGMA query_only=ON"); }
        cache = new TrackedMail();
        return cache;
    }
    private String read(String key) throws SQLException {
        // Keep the lock through the read so a newly enqueued snapshot cannot be missed.
        synchronized (pending) {
            if (failure != null) throw new IllegalStateException("Mail storage failed", failure);
            Update update = pending.get(key);
            if (update != null) return update.yaml;
            try (PreparedStatement ps = reader.prepareStatement("SELECT yaml FROM mail_sections WHERE section_key=?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
            }
        }
    }
    void flush(TrackedMail cache) { cache.flush(); }
    private void enqueue(String key, String yaml) {
        synchronized (pending) {
            if (closing) throw new IllegalStateException("Mail storage is closing");
            while (pending.size() >= MAX_PENDING && !pending.containsKey(key) && failure == null) {
                // Bounded backpressure under a stalled disk, never an unbounded executor queue.
                try { pending.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            if (failure != null) throw new IllegalStateException("Cannot persist mail", failure);
            pending.put(key, new Update(yaml));
            if (!running) { running = true; writer.execute(this::drain); }
        }
    }
    private void drain() {
        while (true) {
            Map<String, Update> batch = new LinkedHashMap<>();
            synchronized (pending) {
                if (pending.isEmpty()) { running = false; pending.notifyAll(); return; }
                for (var entry : pending.entrySet()) { batch.put(entry.getKey(), entry.getValue()); if (batch.size() == 64) break; }
            }
            try {
                persist(batch);
                synchronized (pending) {
                    batch.forEach((key, update) -> { if (pending.get(key) == update) pending.remove(key); });
                    pending.notifyAll();
                }
            } catch (Throwable e) {
                synchronized (pending) { failure = e; running = false; pending.notifyAll(); }
                logger.severe("No se pudo persistir correo; se conservan los cambios pendientes: " + e);
                return;
            }
        }
    }
    private void persist(Map<String, Update> batch) throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement put = connection.prepareStatement("INSERT INTO mail_sections(section_key,yaml) VALUES(?,?) ON CONFLICT(section_key) DO UPDATE SET yaml=excluded.yaml");
             PreparedStatement delete = connection.prepareStatement("DELETE FROM mail_sections WHERE section_key=?")) {
            for (var entry : batch.entrySet()) {
                if (entry.getValue().yaml == null) { delete.setString(1, entry.getKey()); delete.addBatch(); }
                else { put.setString(1, entry.getKey()); put.setString(2, entry.getValue().yaml); put.addBatch(); }
            }
            put.executeBatch(); delete.executeBatch(); connection.commit();
        } catch (SQLException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }
    @Override public void close() throws Exception {
        if (cache != null) cache.flush();
        synchronized (pending) { closing = true; }
        writer.shutdown();
        if (!writer.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Mail writer timeout; connection retained");
        if (reader != null) reader.close();
        if (connection != null) connection.close();
        synchronized (pending) { if (failure != null) throw new IllegalStateException("Mail persistence failed", failure); }
    }
}

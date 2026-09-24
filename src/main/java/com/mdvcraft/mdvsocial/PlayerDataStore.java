package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Almacenamiento SQLite para los datos persistentes de jugadores que antes
 * vivían en player-data.yml.
 *
 * Se mantiene una API mínima parecida a YamlConfiguration para que el resto
 * del plugin no tenga que conocer SQL ni duplicar lógica de títulos/castigos.
 */
final class PlayerDataStore implements AutoCloseable {

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JavaPlugin plugin;
    private final File databaseFile;
    private Connection connection;
    private final java.util.LinkedHashMap<UUID, java.util.Map<String, Object>> profiles = new java.util.LinkedHashMap<>(16, .75f, true);
    private long profileLoads;

    private java.util.Map<String, Object> profile(UUID uuid) throws SQLException {
        java.util.Map<String, Object> result = profiles.get(uuid);
        if (result != null) return result;
        result = new java.util.HashMap<>();
        result.put("last-name", ""); result.put("active", ""); result.put("punishment.active", false);
        result.put("punishment.title", ""); result.put("punishment.previous-title", "");
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_profiles WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) {
                result.put("last-name", rs.getString("last_name")); result.put("active", rs.getString("active_title"));
                result.put("punishment.active", rs.getBoolean("punishment_active"));
                result.put("punishment.title", rs.getString("punishment_title")); result.put("punishment.previous-title", rs.getString("punishment_previous_title"));
            } }
        }
        List<String> unlocked = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT title_id FROM player_unlocked_titles WHERE uuid=? ORDER BY title_id")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) unlocked.add(rs.getString(1)); }
        }
        result.put("unlocked", List.copyOf(unlocked));
        profiles.put(uuid, result); profileLoads++;
        int capacity = Math.max(1, plugin.getConfig().getInt("performance.profile-cache-size", 8192));
        while (profiles.size() > capacity) profiles.remove(profiles.keySet().iterator().next());
        return result;
    }
    synchronized void forget(UUID uuid) { profiles.remove(uuid); }
    synchronized int residentCount() { return profiles.size(); }
    synchronized long profileLoads() { return profileLoads; }

    PlayerDataStore(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
    }

    synchronized void open(File legacyYaml) throws SQLException, IOException {
        closeQuietly(); profiles.clear();
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de datos para SQLite.");
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("No se encontró el driver SQLite dentro del jar de MDVSocial.", ex);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        uuid TEXT PRIMARY KEY,
                        last_name TEXT NOT NULL DEFAULT '',
                        active_title TEXT NOT NULL DEFAULT '',
                        punishment_active INTEGER NOT NULL DEFAULT 0,
                        punishment_title TEXT NOT NULL DEFAULT '',
                        punishment_previous_title TEXT NOT NULL DEFAULT ''
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_unlocked_titles (
                        uuid TEXT NOT NULL,
                        title_id TEXT NOT NULL,
                        PRIMARY KEY (uuid, title_id),
                        FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS storage_meta (
                        meta_key TEXT PRIMARY KEY,
                        meta_value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_unlocked_uuid ON player_unlocked_titles(uuid)");
        }

        migrateLegacyYamlIfPresent(legacyYaml);
    }

    synchronized String getString(String path, String def) {
        ParsedPath p = parse(path);
        if (p == null) return def;
        try { Object value = profile(p.uuid).get(p.child); return value instanceof String s ? s : def; }
        catch (SQLException ex) { throw new IllegalStateException("Cannot read player profile", ex); }
    }
    synchronized boolean getBoolean(String path, boolean def) {
        ParsedPath p = parse(path);
        if (p == null) return def;
        try { Object value = profile(p.uuid).get(p.child); return value instanceof Boolean b ? b : def; }
        catch (SQLException ex) { throw new IllegalStateException("Cannot read player profile", ex); }
    }
    synchronized List<String> getStringList(String path) {
        ParsedPath p = parse(path);
        if (p == null || !p.child.equals("unlocked")) return new ArrayList<>();
        try {
            @SuppressWarnings("unchecked") List<String> values = (List<String>) profile(p.uuid).get("unlocked");
            return new ArrayList<>(values);
        } catch (SQLException ex) { throw new IllegalStateException("Cannot read player profile", ex); }
    }
    synchronized void set(String path, Object value) {
        ParsedPath parsed = parse(path);
        if (parsed == null)
            return;
        try {
            java.util.Map<String, Object> cached = profile(parsed.uuid);
            Object normalized = value;
            if (List.of("last-name", "active", "punishment.title", "punishment.previous-title").contains(parsed.child)) normalized = stringValue(value);
            else if (parsed.child.equals("punishment.active")) normalized = booleanValue(value);
            else if (parsed.child.equals("unlocked")) {
                java.util.TreeSet<String> titles = new java.util.TreeSet<>();
                if (value instanceof Collection<?> values) for (Object raw : values) {
                    String title = stringValue(raw).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                    if (!title.isBlank()) titles.add(title);
                }
                normalized = List.copyOf(titles);
            }
            if (cached.containsKey(parsed.child) && java.util.Objects.equals(cached.get(parsed.child), normalized)) return;
            ensureProfile(parsed.uuid);
            switch (parsed.child) {
                case "last-name" -> updateText(parsed.uuid, "last_name", stringValue(value));
                case "active" -> updateText(parsed.uuid, "active_title", stringValue(value));
                case "punishment.active" -> updateBoolean(parsed.uuid, "punishment_active", booleanValue(value));
                case "punishment.title" -> updateText(parsed.uuid, "punishment_title", stringValue(value));
                case "punishment.previous-title" -> updateText(parsed.uuid, "punishment_previous_title", stringValue(value));
                case "punishment" -> {
                    if (value == null)
                        clearPunishment(parsed.uuid);
                }
                case "unlocked" -> replaceUnlockedTitles(parsed.uuid, value);
                default -> plugin.getLogger().fine("Ruta SQLite ignorada: " + path);
            }
            if (cached.containsKey(parsed.child)) cached.put(parsed.child, normalized);
            else if (parsed.child.equals("punishment") && value == null) {
                cached.put("punishment.active", false); cached.put("punishment.title", ""); cached.put("punishment.previous-title", "");
            }
        } catch (SQLException ex) {
            profiles.remove(parsed.uuid);
            throw new IllegalStateException("Cannot persist player profile", ex);
        }
    }

    /** Las escrituras son inmediatas; se conserva por compatibilidad con saveData(). */
    synchronized void flush() {
        // No-op intencional. Cada mutación usa una sentencia SQLite duradera.
    }

    @Override
    public synchronized void close() throws SQLException { profiles.clear();
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private void closeQuietly() {
        if (connection == null)
            return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }

    private void ensureProfile(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_profiles(uuid) VALUES (?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void updateText(UUID uuid, String column, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_profiles SET " + column + " = ? WHERE uuid = ?")) {
            ps.setString(1, value == null ? "" : value);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void updateBoolean(UUID uuid, String column, boolean value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_profiles SET " + column + " = ? WHERE uuid = ?")) {
            ps.setInt(1, value ? 1 : 0);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void clearPunishment(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE player_profiles
                SET punishment_active = 0,
                    punishment_title = '',
                    punishment_previous_title = ''
                WHERE uuid = ?
                """)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private void replaceUnlockedTitles(UUID uuid, Object value) throws SQLException {
        Collection<?> values = value instanceof Collection<?> collection ? collection : List.of();
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM player_unlocked_titles WHERE uuid = ?")) {
                delete.setString(1, uuid.toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_unlocked_titles(uuid, title_id) VALUES (?, ?)")) {
                for (Object raw : values) {
                    String title = stringValue(raw).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                    if (title.isBlank())
                        continue;
                    insert.setString(1, uuid.toString());
                    insert.setString(2, title);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    private void migrateLegacyYamlIfPresent(File legacyYaml) throws IOException, SQLException {
        if (legacyYaml == null || !legacyYaml.isFile() || legacyYaml.length() == 0L)
            return;

        // Si la importación ya fue confirmada previamente, nunca volvemos a
        // pisar la base con un YAML viejo que haya quedado por un fallo al moverlo.
        if ("true".equalsIgnoreCase(getMeta("legacy_player_data_migrated"))) {
            try {
                archiveLegacyYaml(legacyYaml, 0);
            } catch (IOException archiveError) {
                plugin.getLogger().warning("player-data.yml ya fue migrado anteriormente, pero aún no se pudo retirar: "
                        + archiveError.getMessage());
            }
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyYaml);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null || players.getKeys(false).isEmpty()) {
            try {
                archiveLegacyYaml(legacyYaml, 0);
            } catch (IOException archiveError) {
                plugin.getLogger().warning("player-data.yml está vacío pero no se pudo mover al respaldo: "
                        + archiveError.getMessage());
            }
            return;
        }

        int migrated = 0;
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String rawUuid : players.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(rawUuid);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("UUID inválido omitido al migrar player-data.yml: " + rawUuid);
                    continue;
                }
                ConfigurationSection section = players.getConfigurationSection(rawUuid);
                if (section == null)
                    continue;

                ensureProfile(uuid);
                updateText(uuid, "last_name", section.getString("last-name", ""));
                updateText(uuid, "active_title", section.getString("active", ""));
                updateBoolean(uuid, "punishment_active", section.getBoolean("punishment.active", false));
                updateText(uuid, "punishment_title", section.getString("punishment.title", ""));
                updateText(uuid, "punishment_previous_title", section.getString("punishment.previous-title", ""));
                replaceUnlockedTitlesInsideTransaction(uuid, section.getStringList("unlocked"));
                migrated++;
            }
            setMeta("legacy_player_data_migrated", "true");
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            if (ex instanceof SQLException sql)
                throw sql;
            throw new SQLException("Falló la migración de player-data.yml", ex);
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }

        try {
            archiveLegacyYaml(legacyYaml, migrated);
        } catch (IOException archiveError) {
            plugin.getLogger().warning("Los datos ya fueron migrados a SQLite, pero no se pudo mover player-data.yml al respaldo: "
                    + archiveError.getMessage());
        }
        plugin.getLogger().info("Migración SQLite completada: " + migrated
                + " jugadores importados desde player-data.yml a " + databaseFile.getName() + ".");
    }

    private void replaceUnlockedTitlesInsideTransaction(UUID uuid, Collection<String> values) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM player_unlocked_titles WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_unlocked_titles(uuid, title_id) VALUES (?, ?)")) {
            for (String raw : values) {
                if (raw == null)
                    continue;
                String title = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (title.isBlank())
                    continue;
                insert.setString(1, uuid.toString());
                insert.setString(2, title);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private String getMeta(String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT meta_value FROM storage_meta WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    private void setMeta(String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO storage_meta(meta_key, meta_value) VALUES (?, ?)
                ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value
                """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private void archiveLegacyYaml(File legacyYaml, int migrated) throws IOException {
        File backups = new File(plugin.getDataFolder(), "migration-backups");
        if (!backups.exists() && !backups.mkdirs())
            throw new IOException("No se pudo crear migration-backups para retirar player-data.yml.");
        String stamp = BACKUP_STAMP.format(LocalDateTime.now());
        File target = new File(backups, "player-data-" + stamp + "-" + migrated + "players.legacy.bak");
        int suffix = 1;
        while (target.exists()) {
            target = new File(backups, "player-data-" + stamp + "-" + migrated + "players-" + suffix++ + ".legacy.bak");
        }
        Files.move(legacyYaml.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        plugin.getLogger().info("player-data.yml retirado tras migración. Respaldo: "
                + target.getParentFile().getName() + "/" + target.getName());
    }

    private ParsedPath parse(String path) {
        if (path == null)
            return null;
        String[] parts = path.split("\\.", 3);
        if (parts.length != 3 || !"players".equals(parts[0]))
            return null;
        try {
            return new ParsedPath(UUID.fromString(parts[1]), parts[2]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool)
            return bool;
        if (value instanceof Number number)
            return number.intValue() != 0;
        return Boolean.parseBoolean(stringValue(value));
    }

    private record ParsedPath(UUID uuid, String child) {}
}

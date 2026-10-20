package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Keeps player-data.db and mail-data.db safe to copy:
 * <ul>
 * <li>Every {@code storage.checkpoint-interval-minutes} the -wal files are copied into
 * the .db files, so a backup of the .db alone is at most that old.</li>
 * <li>{@code /mdvsocial backup} writes complete copies of both databases (VACUUM INTO)
 * to {@code backups/<date>/}, keeping the newest {@code storage.backups.keep}.</li>
 * </ul>
 * Closing the stores also checkpoints them (see PlayerDataStore and MailSqliteStore).
 */
final class DatabaseMaintenance {

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MDVSocialPlugin plugin;
    private final Supplier<PlayerDataStore> players;
    private final Supplier<MailSqliteStore> mail;
    private BukkitTask checkpointTask;
    private volatile boolean backupRunning;

    DatabaseMaintenance(MDVSocialPlugin plugin, Supplier<PlayerDataStore> players, Supplier<MailSqliteStore> mail) {
        this.plugin = plugin;
        this.players = players;
        this.mail = mail;
    }

    /** (Re)starts the periodic checkpoint with the current config. */
    void start() {
        stop();
        long minutes = plugin.getConfig().getLong("storage.checkpoint-interval-minutes", 5L);
        if (minutes <= 0)
            return;
        long ticks = minutes * 60L * 20L;
        checkpointTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkpoint, ticks, ticks);
    }

    void stop() {
        if (checkpointTask != null) {
            checkpointTask.cancel();
            checkpointTask = null;
        }
    }

    private void checkpoint() {
        try {
            PlayerDataStore data = players.get();
            if (data != null)
                data.checkpoint();
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo volcar el WAL de player-data.db: " + ex.getMessage());
        }
        try {
            MailSqliteStore store = mail.get();
            if (store != null)
                store.checkpoint();
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo volcar el WAL de mail-data.db: " + ex.getMessage());
        }
    }

    /** /mdvsocial backup: run on the main thread; the copy itself runs async. */
    void backup(CommandSender sender) {
        if (backupRunning) {
            plugin.msg(sender, "backup-running");
            return;
        }
        PlayerDataStore data = players.get();
        MailSqliteStore store = mail.get();
        if (data == null || store == null) {
            plugin.msg(sender, "backup-failed", Map.of("error", "las bases de datos no están abiertas"));
            return;
        }
        // Letters still in memory are queued before the copy, so they are included.
        plugin.saveMailData();
        backupRunning = true;
        plugin.msg(sender, "backup-started");
        File folder = new File(new File(plugin.getDataFolder(), "backups"), STAMP.format(LocalDateTime.now()));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = null;
            try {
                data.backupTo(new File(folder, "player-data.db"));
                store.backupTo(new File(folder, "mail-data.db"));
                prune(folder.getParentFile(), plugin.getConfig().getInt("storage.backups.keep", 10));
            } catch (Exception ex) {
                error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                plugin.getLogger().warning("Falló /mdvsocial backup: " + error);
            }
            String failure = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                backupRunning = false;
                if (failure == null)
                    plugin.msg(sender, "backup-done", Map.of("path", "backups/" + folder.getName()));
                else
                    plugin.msg(sender, "backup-failed", Map.of("error", failure));
            });
        });
    }

    /** Deletes the oldest backup folders beyond {@code keep} (0 or less keeps all). */
    private static void prune(File root, int keep) throws IOException {
        File[] folders = root.listFiles(File::isDirectory);
        if (folders == null)
            return;
        for (File old : expired(Arrays.asList(folders), keep))
            deleteTree(old.toPath());
    }

    /** The folders to delete: all but the newest {@code keep}, by name (a date stamp). */
    static List<File> expired(List<File> folders, int keep) {
        if (keep <= 0 || folders.size() <= keep)
            return List.of();
        List<File> sorted = new ArrayList<>(folders);
        sorted.sort(Comparator.comparing(File::getName));
        return sorted.subList(0, sorted.size() - keep);
    }

    private static void deleteTree(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(p);
        }
    }
}

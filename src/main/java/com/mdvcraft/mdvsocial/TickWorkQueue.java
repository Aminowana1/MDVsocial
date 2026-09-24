package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Shared, bounded main-thread work budget. Bukkit APIs never move to a worker thread. */
final class TickWorkQueue implements AutoCloseable {
    private final JavaPlugin plugin;
    private final LinkedHashMap<String, Job<?>> jobs = new LinkedHashMap<>();
    private final BukkitTask task;
    private record Job<T>(Iterator<T> iterator, Consumer<T> action, Runnable done, boolean required) {
        boolean step() {
            if (!iterator.hasNext()) { done.run(); return true; }
            action.accept(iterator.next());
            if (!iterator.hasNext()) { done.run(); return true; }
            return false;
        }
    }
    TickWorkQueue(JavaPlugin plugin) {
        this.plugin = plugin;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }
    <T> boolean submit(String key, Iterator<T> iterator, Consumer<T> action, Runnable done, boolean required) {
        if (jobs.containsKey(key) || jobs.size() >= 32) return false;
        jobs.put(key, new Job<>(iterator, action, done, required));
        return true;
    }
    boolean containsAny(String... keys) {
        for (String key : keys) if (jobs.containsKey(key)) return true;
        return false;
    }
    private void tick() {
        long until = System.nanoTime() + Math.max(1, Math.min(10, plugin.getConfig().getInt("performance.work-budget-ms", 2))) * 1_000_000L;
        int remaining = Math.max(1, Math.min(256, plugin.getConfig().getInt("performance.work-items-per-tick", 32)));
        while (!jobs.isEmpty() && remaining-- > 0 && System.nanoTime() < until) {
            String key = jobs.keySet().iterator().next();
            Job<?> job = jobs.remove(key);
            try { if (!job.step()) jobs.put(key, job); }
            catch (RuntimeException e) { plugin.getLogger().log(Level.SEVERE, "Trabajo interrumpido: " + key, e); }
        }
    }
    void finishRequiredAndClear() {
        // Shutdown/reload must not silently abandon a broadcast or deletion already accepted.
        while (!jobs.isEmpty()) {
            List<Job<?>> snapshot = List.copyOf(jobs.values());
            jobs.clear();
            for (Job<?> job : snapshot) if (job.required()) while (!job.step()) { }
        }
    }
    @Override public void close() { task.cancel(); finishRequiredAndClear(); }
}

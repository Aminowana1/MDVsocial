package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Moves an accepted requester to the target: optional wait (cancelled by moving
 * after the grace period, taking damage or attacking) and a short damage
 * protection on arrival (lost when attacking).
 */
final class TpaTeleporter {

    /** Damage that protection never blocks, so nobody gets stuck in the void or a border. */
    private static final Set<DamageCause> UNBLOCKABLE = EnumSet.of(
            DamageCause.VOID, DamageCause.SUICIDE, DamageCause.KILL, DamageCause.WORLD_BORDER);

    private final JavaPlugin plugin;
    private final TpaMessages messages;
    private final Supplier<TpaSettings> settings;
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final PlayerDeadlines protection = new PlayerDeadlines();

    /** A requester waiting to be teleported. */
    static final class Warmup {
        final UUID requester;
        final UUID target;
        final long movementLockedFrom;
        BukkitTask task;

        Warmup(UUID requester, UUID target, long movementLockedFrom) {
            this.requester = requester;
            this.target = target;
            this.movementLockedFrom = movementLockedFrom;
        }

        /** Moving only cancels the wait once the grace period is over. */
        boolean movementLocked(long now) {
            return now >= movementLockedFrom;
        }
    }

    TpaTeleporter(JavaPlugin plugin, TpaMessages messages, Supplier<TpaSettings> settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
    }

    /** Teleports now or starts the configured wait. */
    void start(Player requester, Player target) {
        TpaSettings config = settings.get();
        if (config.teleportDelaySeconds() <= 0) {
            teleport(requester, target);
            return;
        }
        cancel(warmups.get(requester.getUniqueId()), null, false);

        long now = System.currentTimeMillis();
        Warmup warmup = new Warmup(requester.getUniqueId(), target.getUniqueId(),
                now + config.movementGraceSeconds() * 1000L);
        warmups.put(warmup.requester, warmup);
        warmup.task = Bukkit.getScheduler().runTaskLater(plugin, () -> finish(warmup),
                config.teleportDelaySeconds() * 20L);

        messages.send(requester, "warmup-started", Map.of(
                "seconds", String.valueOf(config.teleportDelaySeconds()),
                "grace_seconds", String.valueOf(config.movementGraceSeconds()),
                "movement_lock_seconds", String.valueOf(config.movementLockSeconds()),
                "target", target.getName()));
        messages.send(target, "warmup-started-target", Map.of(
                "seconds", String.valueOf(config.teleportDelaySeconds()),
                "player", requester.getName()));
    }

    boolean isWaiting(UUID requester) {
        return warmups.containsKey(requester);
    }

    void onMove(Player player, Location from, Location to) {
        if (!settings.get().cancelDelayOnMove() || to == null)
            return;
        Warmup warmup = warmups.get(player.getUniqueId());
        if (warmup == null || !warmup.movementLocked(System.currentTimeMillis()) || !changedPosition(from, to))
            return;
        cancel(warmup, "warmup-cancelled-move", true);
    }

    void onDamaged(Player player) {
        if (settings.get().cancelDelayOnDamage())
            cancel(warmups.get(player.getUniqueId()), "warmup-cancelled-damage", true);
    }

    void onAttack(Player attacker) {
        if (settings.get().cancelDelayOnAttack())
            cancel(warmups.get(attacker.getUniqueId()), "warmup-cancelled-attack", true);
    }

    void endProtectionByAttack(Player attacker) {
        if (settings.get().invulnerabilityCancelOnAttack()
                && protection.clear(attacker.getUniqueId(), System.currentTimeMillis()))
            messages.send(attacker, "invulnerability-ended-on-attack");
    }

    /** @return true when the damage must be cancelled by the arrival protection. */
    boolean isProtected(Player player, DamageCause cause) {
        return !UNBLOCKABLE.contains(cause) && protection.isActive(player.getUniqueId(), System.currentTimeMillis());
    }

    /** Cancels everything involving a player who left. */
    void forget(UUID player) {
        cancel(warmups.get(player), null, true);
        for (Warmup warmup : List.copyOf(warmups.values())) {
            if (!warmup.target.equals(player))
                continue;
            cancel(warmup, null, false);
            messages.send(Bukkit.getPlayer(warmup.requester), "target-disconnected");
        }
        protection.clear(player, System.currentTimeMillis());
    }

    void shutdown() {
        for (Warmup warmup : List.copyOf(warmups.values()))
            cancel(warmup, null, false);
        protection.clearAll();
    }

    private void finish(Warmup warmup) {
        if (!warmups.remove(warmup.requester, warmup))
            return;
        Player requester = Bukkit.getPlayer(warmup.requester);
        if (requester == null)
            return;
        Player target = Bukkit.getPlayer(warmup.target);
        if (target == null) {
            messages.send(requester, "target-disconnected");
            return;
        }
        if (!settings.get().allowCrossWorld() && !target.getWorld().equals(requester.getWorld())) {
            messages.send(requester, "cross-world-disabled");
            return;
        }
        teleport(requester, target);
    }

    private void teleport(Player requester, Player target) {
        if (!requester.teleport(target.getLocation())) {
            messages.send(requester, "teleport-failed");
            messages.send(target, "teleport-failed");
            return;
        }
        messages.send(requester, "request-accepted", Map.of("target", target.getName()));
        int seconds = settings.get().invulnerabilitySeconds();
        if (seconds > 0) {
            protection.start(requester.getUniqueId(), System.currentTimeMillis(), seconds * 1000L);
            messages.send(requester, "invulnerable", Map.of("seconds", String.valueOf(seconds)));
        }
    }

    /**
     * @param messageKey   sent to the requester, or null for none
     * @param notifyTarget whether the target hears about the cancellation
     */
    private void cancel(Warmup warmup, String messageKey, boolean notifyTarget) {
        if (warmup == null || !warmups.remove(warmup.requester, warmup))
            return;
        if (warmup.task != null)
            warmup.task.cancel();
        Player requester = Bukkit.getPlayer(warmup.requester);
        if (messageKey != null)
            messages.send(requester, messageKey);
        if (notifyTarget)
            messages.send(Bukkit.getPlayer(warmup.target), "warmup-cancelled-target",
                    Map.of("player", requester == null ? "un jugador" : requester.getName()));
    }

    /** Head rotation alone does not count as moving. */
    static boolean changedPosition(Location from, Location to) {
        return from.getWorld() != to.getWorld()
                || from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
    }
}

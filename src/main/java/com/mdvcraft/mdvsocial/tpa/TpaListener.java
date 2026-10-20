package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.BooleanSupplier;

/** Server events that affect a pending teleport. */
final class TpaListener implements Listener {

    private final BooleanSupplier enabled;
    private final TpaService service;
    private final TpaTeleporter teleporter;

    TpaListener(BooleanSupplier enabled, TpaService service, TpaTeleporter teleporter) {
        this.enabled = enabled;
        this.service = service;
        this.teleporter = teleporter;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (enabled.getAsBoolean() && teleporter.isWaiting(event.getPlayer().getUniqueId()))
            teleporter.onMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    /** Arrival protection runs first so a cancelled hit does not cancel anything else. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProtectedDamage(EntityDamageEvent event) {
        if (enabled.getAsBoolean() && event.getEntity() instanceof Player player
                && teleporter.isProtected(player, event.getCause()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (enabled.getAsBoolean() && event.getEntity() instanceof Player player && event.getFinalDamage() > 0.0)
            teleporter.onDamaged(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (enabled.getAsBoolean() && attacker != null)
            teleporter.onAttack(attacker);
    }

    /** Trying to attack ends the arrival protection even when the hit itself was cancelled. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttackAttempt(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (enabled.getAsBoolean() && attacker != null)
            teleporter.endProtectionByAttack(attacker);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player)
            return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player)
            return player;
        return null;
    }
}

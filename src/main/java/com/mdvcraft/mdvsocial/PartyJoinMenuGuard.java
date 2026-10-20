package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps MMOCore from opening its party menu when a player joins a party
 * (MMOCore does it right inside the invite's accept). A player who is about
 * to accept, through MDVSocial or MMOCore's /party accept, is marked for a
 * short moment, and MMOCore's party view opening for them in that moment is
 * cancelled. Opening the menu on purpose later is not affected.
 *
 * Switch: {@code party-invites.open-menu-on-join} (false by default).
 */
final class PartyJoinMenuGuard implements Listener {

    private static final long WINDOW_MILLIS = 2_000L;
    /** MMOCore's party menu holder (EditablePartyView$PartyViewInventory). */
    private static final String PARTY_VIEW = "PartyViewInventory";
    private static final List<String> ACCEPT_COMMANDS = List.of("party accept", "mmocore:party accept");

    private final MDVSocialPlugin plugin;
    private final Map<UUID, Long> joiningUntil = new ConcurrentHashMap<>();

    PartyJoinMenuGuard(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Call right before the player accepts a party invite. */
    void markJoining(Player player) {
        if (player != null && !plugin.getConfig().getBoolean("party-invites.open-menu-on-join", false))
            joiningUntil.put(player.getUniqueId(), System.currentTimeMillis() + WINDOW_MILLIS);
    }

    /** The chat buttons and typed commands accept through MMOCore directly. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isAcceptCommand(event.getMessage()))
            markJoining(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;
        Long until = joiningUntil.get(player.getUniqueId());
        if (until == null)
            return;
        if (System.currentTimeMillis() > until) {
            joiningUntil.remove(player.getUniqueId());
            return;
        }
        Object holder = event.getInventory().getHolder();
        if (holder != null && holder.getClass().getSimpleName().equals(PARTY_VIEW)) {
            joiningUntil.remove(player.getUniqueId());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        joiningUntil.remove(event.getPlayer().getUniqueId());
    }

    static boolean isAcceptCommand(String message) {
        if (message == null || !message.startsWith("/"))
            return false;
        String typed = message.substring(1).trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        for (String command : ACCEPT_COMMANDS)
            if (typed.equals(command) || typed.startsWith(command + " "))
                return true;
        return false;
    }
}

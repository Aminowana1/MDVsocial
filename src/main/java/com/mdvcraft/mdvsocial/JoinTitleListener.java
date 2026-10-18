package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On join, equips {@code settings.join-title.title} (aventurero) to players with
 * {@code settings.join-title.permission} whose saved title is still the default
 * one (forastero) or empty. Titles they chose and punishments are left alone.
 *
 * Switch: {@code settings.join-title.enabled}.
 */
final class JoinTitleListener implements Listener {

    private static final String PATH = "settings.join-title.";
    /** After the permission plugin has loaded the player, before the title check at 20 ticks. */
    private static final long DELAY_TICKS = 10L;

    private final MDVSocialPlugin plugin;

    JoinTitleListener(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> grant(player), DELAY_TICKS);
    }

    private void grant(Player player) {
        if (!player.isOnline() || !plugin.getConfig().getBoolean(PATH + "enabled", true))
            return;
        String permission = plugin.getConfig().getString(PATH + "permission", "group.aventurero");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission))
            return;
        plugin.equipTitleIfDefault(player, plugin.getConfig().getString(PATH + "title", "aventurero"));
    }
}

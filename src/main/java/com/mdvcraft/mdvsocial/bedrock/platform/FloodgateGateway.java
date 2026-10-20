package com.mdvcraft.mdvsocial.bedrock.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.Form;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/**
 * Single access point to Floodgate. Every Bedrock form in the plugin is sent
 * through here, so Floodgate detection and error handling live in one place.
 */
public final class FloodgateGateway {

    private final JavaPlugin plugin;
    private boolean floodgateAvailable;

    public FloodgateGateway(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    public boolean isAvailable() {
        return floodgateAvailable;
    }

    public boolean isBedrock(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("bedrock.enabled", true))
            return false;
        if (!floodgateAvailable)
            refresh();
        if (!floodgateAvailable)
            return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** @return the Floodgate player, or null when unavailable. */
    public FloodgatePlayer floodgatePlayer(Player player) {
        if (player == null || !isBedrock(player))
            return null;
        try {
            return FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean send(Player player, Form form) {
        FloodgatePlayer floodgatePlayer = floodgatePlayer(player);
        if (floodgatePlayer == null)
            return false;
        try {
            return floodgatePlayer.sendForm(form);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar Form Bedrock a " + player.getName() + ": "
                    + ex.getMessage());
            return false;
        }
    }

    /**
     * Closes the form a Bedrock player has open. Floodgate cannot do it; Geyser
     * can, but only when Geyser runs on this server (not on a proxy), so this
     * is best effort and does nothing otherwise.
     */
    public void closeForm(Player player) {
        try {
            Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object geyser = api.getMethod("api").invoke(null);
            Object connection = api.getMethod("connectionByUuid", java.util.UUID.class)
                    .invoke(geyser, player.getUniqueId());
            if (connection != null)
                connection.getClass().getMethod("closeForm").invoke(connection);
        } catch (Throwable ignored) {
            // Geyser not on this server or an API without closeForm.
        }
    }
}

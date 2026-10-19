package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reads MDVClans through its public methods, by reflection (MDVClans is optional).
 * Every call degrades to "no clan" when MDVClans is missing or older.
 */
final class MDVClansBridge {

    private MDVClansBridge() {
    }

    static boolean available() {
        return plugin() != null;
    }

    static boolean isInClan(UUID player) {
        Object result = call("isPlayerInClan", player);
        return Boolean.TRUE.equals(result);
    }

    /** Members of {@code player}'s clan, without {@code player}; empty without a clan. */
    static Set<UUID> clanMates(UUID player) {
        Set<UUID> out = new HashSet<>();
        if (call("getClanMemberUuids", player) instanceof Collection<?> members)
            for (Object member : members)
                if (member instanceof UUID uuid && !uuid.equals(player))
                    out.add(uuid);
        return out;
    }

    /** Sends the clan invite through MDVClans' own command (permissions, limits, messages). */
    static void invite(Player inviter, String targetName) {
        Bukkit.dispatchCommand(inviter, "clan invitar " + targetName);
    }

    private static Plugin plugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MDVClans");
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private static Object call(String method, UUID player) {
        Plugin plugin = plugin();
        if (plugin == null || player == null)
            return null;
        try {
            Method m = plugin.getClass().getMethod(method, UUID.class);
            return m.invoke(plugin, player);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

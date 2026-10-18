package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Online player lookup and name completion shared by the social commands. */
public final class OnlinePlayers {

    private OnlinePlayers() {
    }

    /** Exact name first, then ignoring case; null when nobody matches. */
    public static Player find(String name) {
        if (name == null || name.isBlank())
            return null;
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null)
            return exact;
        for (Player online : Bukkit.getOnlinePlayers())
            if (online.getName().equalsIgnoreCase(name))
                return online;
        return null;
    }

    /**
     * Names of online players {@code viewer} can see that start with
     * {@code prefix}: friends first, then everyone else, each alphabetically.
     *
     * @param excluded lower-case names left out (e.g. the ones already typed)
     */
    public static List<String> complete(Player viewer, String prefix, Collection<String> excluded,
            Collection<UUID> friends) {
        String start = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        Set<UUID> friendIds = new HashSet<>(friends);
        List<String> friendNames = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (online.equals(viewer) || !viewer.canSee(online) || excluded.contains(lower) || !lower.startsWith(start))
                continue;
            (friendIds.contains(online.getUniqueId()) ? friendNames : others).add(name);
        }
        friendNames.sort(String.CASE_INSENSITIVE_ORDER);
        others.sort(String.CASE_INSENSITIVE_ORDER);
        friendNames.addAll(others);
        return friendNames;
    }
}

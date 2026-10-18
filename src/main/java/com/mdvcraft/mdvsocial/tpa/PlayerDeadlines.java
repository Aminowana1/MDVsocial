package com.mdvcraft.mdvsocial.tpa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** "Until when" per player, used for request cooldowns and arrival protection. */
final class PlayerDeadlines {

    private final Map<UUID, Long> until = new HashMap<>();

    void start(UUID player, long now, long durationMillis) {
        if (durationMillis > 0)
            until.put(player, now + durationMillis);
    }

    boolean isActive(UUID player, long now) {
        return remainingMillis(player, now) > 0;
    }

    long remainingMillis(UUID player, long now) {
        Long end = until.get(player);
        if (end == null)
            return 0L;
        if (now >= end) {
            until.remove(player);
            return 0L;
        }
        return end - now;
    }

    long remainingSeconds(UUID player, long now) {
        return (remainingMillis(player, now) + 999L) / 1000L;
    }

    /** @return true when the player had an active deadline. */
    boolean clear(UUID player, long now) {
        boolean active = isActive(player, now);
        until.remove(player);
        return active;
    }

    void clearAll() {
        until.clear();
    }
}

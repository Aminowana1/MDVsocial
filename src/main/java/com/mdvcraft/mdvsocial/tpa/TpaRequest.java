package com.mdvcraft.mdvsocial.tpa;

import java.util.UUID;

/** "{@code sender} wants to teleport to {@code target}". */
public record TpaRequest(UUID sender, UUID target, long createdAtMillis, long expiresAtMillis) {

    public boolean isExpired(long now) {
        return now >= expiresAtMillis;
    }

    public long remainingSeconds(long now) {
        return Math.max(0L, (expiresAtMillis - now + 999L) / 1000L);
    }
}

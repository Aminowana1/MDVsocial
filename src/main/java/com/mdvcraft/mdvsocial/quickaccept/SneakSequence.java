package com.mdvcraft.mdvsocial.quickaccept;

/**
 * Counts quick consecutive sneaks. Pure logic (times in milliseconds) so it
 * can be tested without a server.
 */
public final class SneakSequence {

    public enum Result {
        /** The offer is over; nothing happens. */
        EXPIRED,
        /** Sneak counted, more are needed. */
        PROGRESS,
        /** Enough quick sneaks: the request is accepted. */
        COMPLETE
    }

    private final int required;
    private final long maxGapMillis;
    private final long expiresAt;
    private int count;
    private long lastSneakAt = Long.MIN_VALUE;

    public SneakSequence(int required, long maxGapMillis, long expiresAt) {
        this.required = Math.max(1, required);
        this.maxGapMillis = maxGapMillis;
        this.expiresAt = expiresAt;
    }

    public Result register(long now) {
        if (now > expiresAt)
            return Result.EXPIRED;
        // A slow sneak starts a new sequence instead of counting towards the old one.
        if (count == 0 || now - lastSneakAt > maxGapMillis)
            count = 0;
        count++;
        lastSneakAt = now;
        return count >= required ? Result.COMPLETE : Result.PROGRESS;
    }

    public boolean isExpired(long now) {
        return now > expiresAt;
    }
}

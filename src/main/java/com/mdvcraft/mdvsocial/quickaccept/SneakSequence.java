package com.mdvcraft.mdvsocial.quickaccept;

/**
 * Counts quick consecutive sneaks. Pure logic (times in milliseconds) so it
 * can be tested without a server.
 *
 * Tolerant of fast tapping:
 * <ul>
 * <li>The gap is measured from when the player last stood up, so holding a
 * sneak a little longer does not break the sequence.</li>
 * <li>If the server saw the player stand up without seeing them crouch first
 * (the press was lost or cancelled by another plugin), that counts as a sneak.</li>
 * </ul>
 */
public final class SneakSequence {

    public enum Result {
        /** The offer is over; nothing happens. */
        EXPIRED,
        /** Sneak counted, more are needed (or nothing to count). */
        PROGRESS,
        /** Enough quick sneaks: the request is accepted. */
        COMPLETE
    }

    private final int required;
    private final long maxGapMillis;
    private final long expiresAt;
    private int count;
    private long lastActivityAt = Long.MIN_VALUE;
    private boolean crouched;

    public SneakSequence(int required, long maxGapMillis, long expiresAt) {
        this.required = Math.max(1, required);
        this.maxGapMillis = maxGapMillis;
        this.expiresAt = expiresAt;
    }

    /** A crouch (sneak pressed). */
    public Result register(long now) {
        return register(true, now);
    }

    /**
     * @param sneaking true when the player crouched, false when they stood up
     */
    public Result register(boolean sneaking, long now) {
        if (now > expiresAt)
            return Result.EXPIRED;
        if (sneaking) {
            crouched = true;
            return countSneak(now);
        }
        boolean missedPress = !crouched;
        crouched = false;
        if (missedPress)
            return countSneak(now);
        // A normal stand-up only moves the gap reference forward.
        lastActivityAt = now;
        return Result.PROGRESS;
    }

    private Result countSneak(long now) {
        // A slow sneak starts a new sequence instead of counting towards the old one.
        if (count == 0 || now - lastActivityAt > maxGapMillis)
            count = 0;
        count++;
        lastActivityAt = now;
        return count >= required ? Result.COMPLETE : Result.PROGRESS;
    }

    public boolean isExpired(long now) {
        return now > expiresAt;
    }
}

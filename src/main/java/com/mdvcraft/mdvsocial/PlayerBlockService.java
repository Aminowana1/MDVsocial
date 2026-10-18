package com.mdvcraft.mdvsocial;

import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * General player block list, shared by every MDVSocial feature.
 *
 * The only question other systems need to ask is
 * {@link #isBlocked(UUID, UUID)}: "has {@code owner} blocked {@code target}?".
 * Mail uses it to refuse letters; future features (private messages, invites...)
 * can call the same method through {@link MDVSocialAPI}.
 */
public final class PlayerBlockService {

    public enum BlockResult {
        BLOCKED,
        ALREADY_BLOCKED,
        SELF,
        LIMIT_REACHED
    }

    private final PlayerDataStore store;
    private final IntSupplier maxEntries;

    PlayerBlockService(PlayerDataStore store, IntSupplier maxEntries) {
        this.store = store;
        this.maxEntries = maxEntries;
    }

    /** @return true when {@code owner} has blocked {@code target}. */
    public boolean isBlocked(UUID owner, UUID target) {
        if (owner == null || target == null || owner.equals(target))
            return false;
        return store.blockedPlayers(owner).contains(target);
    }

    /** @return true when {@code from} may contact {@code to} (i.e. {@code to} has not blocked {@code from}). */
    public boolean canContact(UUID from, UUID to) {
        return !isBlocked(to, from);
    }

    /** Players blocked by {@code owner}, oldest first. */
    public List<UUID> blockedPlayers(UUID owner) {
        return owner == null ? List.of() : store.blockedPlayers(owner);
    }

    public BlockResult block(UUID owner, UUID target) {
        if (owner.equals(target))
            return BlockResult.SELF;
        List<UUID> current = store.blockedPlayers(owner);
        if (current.contains(target))
            return BlockResult.ALREADY_BLOCKED;
        int max = maxEntries.getAsInt();
        if (max > 0 && current.size() >= max)
            return BlockResult.LIMIT_REACHED;
        return store.addBlock(owner, target) ? BlockResult.BLOCKED : BlockResult.ALREADY_BLOCKED;
    }

    /** @return false when {@code target} was not blocked. */
    public boolean unblock(UUID owner, UUID target) {
        return store.removeBlock(owner, target);
    }
}

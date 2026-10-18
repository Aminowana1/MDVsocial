package com.mdvcraft.mdvsocial.bedrock.port;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** General player block list used by the Bedrock "Jugadores Bloqueados" forms. */
public interface BlocksPort {

    /** Players blocked by {@code player}, oldest first. */
    List<UUID> blocked(Player player);

    /** Blocks by name and tells the player the result. */
    void blockByName(Player player, String targetName);

    /** Unblocks by name and tells the player the result. */
    void unblockByName(Player player, String targetName);

    /** Unblocks and tells the player the result. */
    void unblock(Player player, UUID target);
}

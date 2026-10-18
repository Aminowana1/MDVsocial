package com.mdvcraft.mdvsocial.bedrock.port;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Teleport requests (TPA) used by the Bedrock "Viajes" forms. */
public interface TeleportPort {

    /** A received request: who sent it and how long it can still be answered. */
    record RequestView(UUID sender, String senderName, long remainingSeconds) {
    }

    /** @return true when the player may use the TPA; otherwise they were told why not. */
    boolean checkAccess(Player player);

    /** Valid requests received by the player, newest first. */
    List<RequestView> pending(Player player);

    /** Name of the player the viewer sent a pending request to, or null. */
    String outgoingTarget(Player player);

    void request(Player player, Player target);

    /** @return true when the request was still valid and is now accepted. */
    boolean accept(Player player, UUID sender);

    /** @return true when the request was still valid and is now denied. */
    boolean deny(Player player, UUID sender);

    void cancelOutgoing(Player player);
}

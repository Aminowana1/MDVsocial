package com.mdvcraft.mdvsocial.quickaccept;

import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Something a player can accept by sneaking quickly, e.g. a party invitation,
 * a teleport request or a friend request.
 *
 * @param type      settings key under {@code quick-accept.types} (e.g. "party-invite")
 * @param onAccept  runs on the main thread when the gesture is completed
 * @param stillValid checked right before {@code onAccept}; false drops the offer silently
 *                   (for example when the invitation was already answered from a menu)
 */
public record QuickAcceptRequest(String type, Consumer<Player> onAccept, Predicate<Player> stillValid) {

    public QuickAcceptRequest {
        if (stillValid == null)
            stillValid = player -> true;
    }

    public QuickAcceptRequest(String type, Consumer<Player> onAccept) {
        this(type, onAccept, null);
    }
}

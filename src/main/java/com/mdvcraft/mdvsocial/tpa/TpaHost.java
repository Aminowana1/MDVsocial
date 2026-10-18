package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;

/**
 * What the TPA module needs from the rest of MDVSocial. The module never
 * touches the main plugin class directly.
 */
public interface TpaHost {

    JavaPlugin plugin();

    /** Translates {@code &} color codes. */
    String color(String raw);

    boolean isBedrock(Player player);

    /** @return false when {@code to} has blocked {@code from}. */
    boolean canContact(UUID from, UUID to);

    Collection<UUID> friends(Player player);

    /** Whether both players are in the same adventure party. */
    boolean sameParty(Player a, Player b);

    /** Closes every menu the player has open (inventory menus and Bedrock forms). */
    void closeMenus(Player player);

    /** Bedrock confirmation form for one received request (Aceptar / Rechazar / Más tarde). */
    void openBedrockRequest(Player target, TpaRequest request);
}

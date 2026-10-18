package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Feedback for the Java chat buttons of party invites and friend requests
 * ({@code /mmocore:party|friends accept|deny <uuid>}). MMOCore stays silent in
 * two cases, so MDVSocial answers them:
 * <ul>
 * <li>Denying a party invite: MMOCore sends nothing, so it is denied here and
 * {@code party-request-denied} is sent.</li>
 * <li>A button whose request is gone (accepted, denied, expired or withdrawn):
 * the command is cancelled and {@code request-no-longer-available} is sent.
 * A chat line can't be changed once sent, so its tooltip can't say it.</li>
 * </ul>
 * Everything else (accepting, friend denials) is left to MMOCore.
 */
final class MMOCoreRequestClicks implements Listener {

    enum Kind { PARTY, FRIENDS }

    /** A typed {@code party|friends accept|deny <uuid>} command. */
    record Click(Kind kind, boolean accept, UUID requestId) {
    }

    private final MDVSocialPlugin plugin;

    MMOCoreRequestClicks(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Click click = parse(event.getMessage());
        if (click == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return;
        Player player = event.getPlayer();
        Object request = plugin.getMMOCoreRequest(click.requestId());
        if (!plugin.isMMOCoreRequestAvailable(player, request)) {
            event.setCancelled(true);
            plugin.msg(player, "request-no-longer-available");
            return;
        }
        if (click.kind() == Kind.PARTY && !click.accept()) {
            event.setCancelled(true);
            String creator = plugin.getMMOCoreRequestCreatorName(request);
            if (plugin.resolveMMOCoreRequest(player, request, false))
                plugin.msg(player, "party-request-denied", Map.of("target", creator));
        }
    }

    /** Null unless the message is one of the buttons' commands with a valid UUID. */
    static Click parse(String message) {
        if (message == null || !message.startsWith("/"))
            return null;
        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length != 3)
            return null;
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("mmocore:"))
            root = root.substring("mmocore:".length());
        Kind kind = switch (root) {
            case "party" -> Kind.PARTY;
            case "friends" -> Kind.FRIENDS;
            default -> null;
        };
        String action = parts[1].toLowerCase(Locale.ROOT);
        if (kind == null || !(action.equals("accept") || action.equals("deny")))
            return null;
        try {
            return new Click(kind, action.equals("accept"), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

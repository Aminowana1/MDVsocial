package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;

/**
 * Applies the block list to MMOCore's own invite command typed by players
 * (e.g. "/party invite Bob"), which would otherwise bypass MDVSocial.
 * MDVSocial's own invitations use dispatchCommand and are checked before.
 */
final class PartyInviteGuard implements Listener {

    private final MDVSocialPlugin plugin;

    PartyInviteGuard(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        List<String> commands = plugin.getConfig().getStringList("blocked-players.party-invite-commands");
        if (commands.isEmpty())
            commands = List.of("party invite");
        String targetName = invitedName(event.getMessage(), commands);
        if (targetName == null)
            return;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null)
            return;
        Player inviter = event.getPlayer();
        if (plugin.isPartyInviteBlocked(inviter, target.getUniqueId(), target.getName()))
            event.setCancelled(true);
    }

    /**
     * @return the player name after one of the invite commands
     *         ("/party invite Bob" with "party invite" -> "Bob"), or null.
     */
    static String invitedName(String message, List<String> commands) {
        if (message == null || !message.startsWith("/"))
            return null;
        String typed = message.substring(1).trim().replaceAll("\\s+", " ");
        String lower = typed.toLowerCase(Locale.ROOT);
        for (String raw : commands) {
            if (raw == null || raw.isBlank())
                continue;
            String command = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (command.startsWith("/"))
                command = command.substring(1);
            if (!lower.startsWith(command + " "))
                continue;
            String rest = typed.substring(command.length() + 1).trim();
            if (rest.isEmpty())
                return null;
            int space = rest.indexOf(' ');
            return space < 0 ? rest : rest.substring(0, space);
        }
        return null;
    }
}

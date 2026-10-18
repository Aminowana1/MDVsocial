package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * /grupo [jugador...]: creates an adventure party (if the player has none) and
 * invites up to {@link #MAX_INVITES} online players. Bedrock players get the
 * native party form afterwards; Java players get the configured party menu.
 */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    static final int MAX_INVITES = 5;

    private final MDVSocialPlugin plugin;

    PartyCommand(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        PluginCommand command = plugin.getCommand("grupo");
        if (command == null) {
            plugin.getLogger().warning("No se pudo registrar /grupo porque no existe en plugin.yml.");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg(sender, "only-players");
            return true;
        }
        if (!player.hasPermission("mdvsocial.party.use")) {
            plugin.msg(player, "no-permission");
            return true;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            plugin.msg(player, "party-mmocore-missing");
            return true;
        }
        List<String> names = distinctNames(args);
        if (names.size() > MAX_INVITES) {
            plugin.msg(player, "party-command-too-many", Map.of("max", String.valueOf(MAX_INVITES)));
            return true;
        }

        if (plugin.getMMOCoreParty(player) == null) {
            if (!plugin.createPartyFor(player)) {
                plugin.msg(player, "party-create-error");
                return true;
            }
            plugin.msg(player, "party-created");
        } else if (names.isEmpty()) {
            plugin.msg(player, "party-already-in-party");
        }

        for (String name : names) {
            Player target = OnlinePlayers.find(name);
            if (target == null || !player.canSee(target))
                plugin.msg(player, "party-target-offline", Map.of("target", name));
            else
                plugin.invitePlayerToParty(player, target);
        }
        openMenu(player, names.isEmpty());
        return true;
    }

    private void openMenu(Player player, boolean nothingInvited) {
        if (plugin.isBedrockPlayer(player)) {
            plugin.bedrock().party().open(player);
            return;
        }
        // Java: keep the chat visible when invitations were just sent.
        if (!nothingInvited)
            return;
        String menuCommand = plugin.getConfig().getString("party-command.java-menu-command", "party");
        if (menuCommand == null || menuCommand.isBlank())
            return;
        Bukkit.dispatchCommand(player, menuCommand.startsWith("/") ? menuCommand.substring(1) : menuCommand);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length == 0 || args.length > MAX_INVITES)
            return List.of();
        Set<String> typed = new HashSet<>();
        for (int i = 0; i < args.length - 1; i++)
            typed.add(args[i].toLowerCase(Locale.ROOT));
        return OnlinePlayers.complete(player, args[args.length - 1], typed, plugin.getMMOCoreFriendUuids(player));
    }

    /** Non-blank names without duplicates (case-insensitive), in the order typed. */
    static List<String> distinctNames(String[] args) {
        Set<String> seen = new HashSet<>();
        Set<String> out = new LinkedHashSet<>();
        for (String raw : args) {
            if (raw == null || raw.isBlank())
                continue;
            String name = raw.trim();
            if (seen.add(name.toLowerCase(Locale.ROOT)))
                out.add(name);
        }
        return new ArrayList<>(out);
    }
}

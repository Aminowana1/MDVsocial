package com.mdvcraft.mdvsocial.tpa;

import com.mdvcraft.mdvsocial.OnlinePlayers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /tpa &lt;jugador|aceptar|rechazar|cancelar|ayuda&gt; plus the shortcuts
 * /tpaccept, /tpdeny, /aceptartp, /rechazartp and /tpacancel. The chat buttons
 * use /tpa aceptarsolicitud|rechazarsolicitud &lt;uuid&gt; to answer one
 * specific request.
 */
final class TpaCommand implements CommandExecutor, TabCompleter {

    static final List<String> COMMANDS = List.of("tpa", "tpaccept", "tpdeny", "aceptartp", "rechazartp", "tpacancel");

    enum Action { REQUEST, ACCEPT, DENY, ACCEPT_ONE, DENY_ONE, CANCEL, HELP }

    /** What the player asked for and its argument (a name or a request UUID). */
    record Parsed(Action action, String argument) {
    }

    private static final Map<String, Action> SHORTCUTS = Map.of(
            "tpaccept", Action.ACCEPT, "aceptartp", Action.ACCEPT,
            "tpdeny", Action.DENY, "rechazartp", Action.DENY,
            "tpacancel", Action.CANCEL);

    private static final Map<String, Action> SUBCOMMANDS = Map.ofEntries(
            Map.entry("aceptar", Action.ACCEPT), Map.entry("accept", Action.ACCEPT),
            Map.entry("rechazar", Action.DENY), Map.entry("deny", Action.DENY), Map.entry("denegar", Action.DENY),
            Map.entry("cancelar", Action.CANCEL), Map.entry("cancel", Action.CANCEL),
            Map.entry("ayuda", Action.HELP), Map.entry("help", Action.HELP),
            Map.entry("aceptarsolicitud", Action.ACCEPT_ONE), Map.entry("acceptrequest", Action.ACCEPT_ONE),
            Map.entry("rechazarsolicitud", Action.DENY_ONE), Map.entry("denyrequest", Action.DENY_ONE));

    private static final List<String> SUGGESTED = List.of("aceptar", "rechazar", "cancelar", "ayuda");

    private final TpaHost host;
    private final TpaService service;
    private final TpaMessages messages;

    TpaCommand(TpaHost host, TpaService service, TpaMessages messages) {
        this.host = host;
        this.service = service;
        this.messages = messages;
    }

    void register() {
        for (String name : COMMANDS) {
            PluginCommand command = host.plugin().getCommand(name);
            if (command == null) {
                host.plugin().getLogger().warning("No se pudo registrar /" + name + " porque no existe en plugin.yml.");
                continue;
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "only-players");
            return true;
        }
        if (!service.checkAccess(player))
            return true;
        Parsed parsed = parse(command.getName(), args);
        switch (parsed.action()) {
            case REQUEST -> service.request(player, OnlinePlayers.find(parsed.argument()));
            case ACCEPT -> service.acceptLatest(player);
            case DENY -> service.denyLatest(player);
            case ACCEPT_ONE -> service.accept(player, uuidOrNull(parsed.argument()));
            case DENY_ONE -> service.deny(player, uuidOrNull(parsed.argument()));
            case CANCEL -> service.cancelOutgoing(player);
            case HELP -> messages.sendHelp(player);
        }
        return true;
    }

    static Parsed parse(String commandName, String[] args) {
        Action shortcut = SHORTCUTS.get(commandName.toLowerCase(Locale.ROOT));
        if (shortcut != null)
            return new Parsed(shortcut, null);
        if (args.length == 0)
            return new Parsed(Action.HELP, null);
        Action action = SUBCOMMANDS.get(args[0].toLowerCase(Locale.ROOT).replace("-", "").replace("_", ""));
        if (action == null)
            return new Parsed(Action.REQUEST, args[0]);
        if (action == Action.ACCEPT_ONE || action == Action.DENY_ONE)
            return args.length >= 2 ? new Parsed(action, args[1])
                    : new Parsed(action == Action.ACCEPT_ONE ? Action.ACCEPT : Action.DENY, null);
        return new Parsed(action, null);
    }

    private static UUID uuidOrNull(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !command.getName().equalsIgnoreCase("tpa") || args.length != 1)
            return List.of();
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(OnlinePlayers.complete(player, typed, Set.of(), host.friends(player)));
        for (String sub : SUGGESTED)
            if (sub.startsWith(typed))
                out.add(sub);
        return out;
    }
}

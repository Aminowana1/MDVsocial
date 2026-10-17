package com.mdvcraft.mdvsocial.bedrock.action;

import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuButton;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuContext;
import com.mdvcraft.mdvsocial.bedrock.port.BedrockHost;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps canonical action names to handlers and dispatches button clicks.
 * New actions are added with {@link #register} instead of growing a switch.
 */
public final class BedrockActionRegistry {

    private final BedrockHost host;
    private final Map<String, BedrockAction> actions = new ConcurrentHashMap<>();

    public BedrockActionRegistry(BedrockHost host) {
        this.host = host;
    }

    /** Registers a handler under one or more canonical action names. */
    public void register(BedrockAction action, String... names) {
        for (String name : names)
            actions.put(name.toUpperCase(Locale.ROOT), action);
    }

    public boolean isRegistered(String action) {
        return actions.containsKey(normalize(action));
    }

    /** Java aliases first (shared with inventory menus), then Bedrock-only aliases. */
    public String normalize(String action) {
        return normalizeBedrockAliases(host.normalizeAction(action));
    }

    static String normalizeBedrockAliases(String normalized) {
        return switch (normalized == null ? "" : normalized) {
            case "OPEN_FRIENDS_BEDROCK", "BEDROCK_FRIENDS", "OPEN_BEDROCK_FRIENDS" -> "OPEN_BEDROCK_FRIENDS";
            case "OPEN_PARTY_BEDROCK", "BEDROCK_PARTY", "OPEN_BEDROCK_PARTY" -> "OPEN_BEDROCK_PARTY";
            case "OPEN_MMOCORE_PROFILE", "MMOCORE_PROFILE", "OPEN_BEDROCK_MMOCORE_PROFILE" -> "OPEN_MMOCORE_PROFILE";
            case "OPEN_MMOCORE_ATTRIBUTES", "MMOCORE_ATTRIBUTES", "OPEN_BEDROCK_MMOCORE_ATTRIBUTES" ->
                "OPEN_MMOCORE_ATTRIBUTES";
            case "OPEN_MMOCORE_CLASSES", "MMOCORE_CLASSES", "OPEN_BEDROCK_MMOCORE_CLASSES" -> "OPEN_MMOCORE_CLASSES";
            case "NONE", "INFO", "NO_ACTION" -> "NONE";
            default -> normalized == null ? "" : normalized;
        };
    }

    public void dispatch(Player player, BedrockMenuButton button, BedrockMenuContext context) {
        if (player == null || button == null || context == null)
            return;
        if (!button.permission().isBlank() && !player.hasPermission(button.permission())) {
            host.message(player, "no-permission");
            host.playUiSound(player, "invalid");
            return;
        }

        String action = normalize(button.action());
        host.playUiSound(player, button.sound().isBlank() ? action : button.sound());

        BedrockAction handler = actions.get(action);
        if (handler == null) {
            player.sendMessage(host.color(host.prefix() + "&cAcción Bedrock no reconocida: &e" + action));
            host.plugin().getLogger().warning("Acción Bedrock no reconocida en " + context.menuId() + ": " + action);
            return;
        }
        handler.execute(player, button, context);
    }
}

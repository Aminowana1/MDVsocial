package com.mdvcraft.mdvsocial.bedrock.port;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * Services the Bedrock module needs from the host plugin. The module never
 * touches MDVSocialPlugin directly; it only talks through this port and the
 * domain ports (titles, mail, social).
 */
public interface BedrockHost {

    JavaPlugin plugin();

    String color(String raw);

    String prefix();

    String applyPlayerPlaceholders(String raw, Player player);

    String applyTargetPlaceholders(String raw, Player player, UUID targetUuid, String targetName,
            boolean targetOnline);

    /** Java-side action alias normalization shared with inventory menus. */
    String normalizeAction(String raw);

    void message(CommandSender sender, String key);

    void message(CommandSender sender, String key, Map<String, String> replacements);

    void playUiSound(Player player, String soundKey);

    boolean evaluateCondition(Player player, String placeholder, String expected);

    /**
     * Opens a configured social menu. The host routes Bedrock players back to
     * the module and applies the Java fallback when configured.
     */
    void openMenu(Player player, String menuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline);

    void openStartMenu(Player player);

    void openItemBrowser(Player player);
}

package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.bedrock.port.BedrockHost;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/** Exposes MDVSocialPlugin services to the Bedrock module. */
final class BedrockHostAdapter implements BedrockHost {

    private final MDVSocialPlugin plugin;

    BedrockHostAdapter(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public String color(String raw) {
        return plugin.color(raw);
    }

    @Override
    public String prefix() {
        return plugin.getPrefix();
    }

    @Override
    public String applyPlayerPlaceholders(String raw, Player player) {
        return plugin.applyPlayerPlaceholders(raw, player);
    }

    @Override
    public String applyTargetPlaceholders(String raw, Player player, UUID targetUuid, String targetName,
            boolean targetOnline) {
        return plugin.applyTargetPlaceholders(raw, player, targetUuid, targetName, targetOnline);
    }

    @Override
    public String normalizeAction(String raw) {
        return plugin.normalizeAction(raw);
    }

    @Override
    public void message(CommandSender sender, String key) {
        plugin.msg(sender, key);
    }

    @Override
    public void message(CommandSender sender, String key, Map<String, String> replacements) {
        plugin.msg(sender, key, replacements);
    }

    @Override
    public void playUiSound(Player player, String soundKey) {
        plugin.playUiSound(player, soundKey);
    }

    @Override
    public boolean evaluateCondition(Player player, String placeholder, String expected) {
        return plugin.evaluateMenuCondition(player, placeholder, expected);
    }

    @Override
    public void openMenu(Player player, String menuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline) {
        plugin.openCustomMenu(player, menuId, page, previousMenu, previousPage, targetUuid, targetName,
                targetOnline);
    }

    @Override
    public void openStartMenu(Player player) {
        plugin.openSocialStart(player);
    }

    @Override
    public void openItemBrowser(Player player) {
        plugin.openMMOItemsBrowser(player);
    }
}

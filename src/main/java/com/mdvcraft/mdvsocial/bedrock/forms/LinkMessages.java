package com.mdvcraft.mdvsocial.bedrock.forms;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Chat fallbacks for the store and Discord links when the Bedrock "tienda" or
 * "discord" form cannot be opened (Bedrock has no clickable chat links).
 */
public final class LinkMessages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final BedrockFormSupport forms;

    public LinkMessages(BedrockFormSupport forms) {
        this.forms = forms;
    }

    public void sendShop(Player player) {
        sendShop(player, config().getString("shop.url", ""));
    }

    public void sendShop(Player player, String url) {
        String template = config().getString("shop.bedrock.fallback-message",
                "&6&lTienda online\\n&fVisita: &b{shop}");
        player.sendMessage(LEGACY.deserialize(template.replace("{shop}", url)));
    }

    public void sendDiscord(Player player) {
        sendDiscord(player, config().getString("discord.url", ""));
    }

    public void sendDiscord(Player player, String url) {
        String template = config().getString("discord.bedrock.fallback-message",
                "&6&lDiscord\\n&fUnete a nuestra comunidad: &b{discord}");
        player.sendMessage(LEGACY.deserialize(template.replace("{discord}", url)));
    }

    private FileConfiguration config() {
        return forms.host().plugin().getConfig();
    }
}

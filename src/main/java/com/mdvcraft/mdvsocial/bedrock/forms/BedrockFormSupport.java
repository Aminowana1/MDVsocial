package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.menu.BedrockMenuRepository;
import com.mdvcraft.mdvsocial.bedrock.platform.FloodgateGateway;
import com.mdvcraft.mdvsocial.bedrock.port.BedrockHost;
import com.mdvcraft.mdvsocial.bedrock.session.BedrockUiSessionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared building blocks for every Bedrock form: text rendering, YAML-driven
 * buttons, session handling and sending through Floodgate.
 */
public final class BedrockFormSupport {

    private final BedrockHost host;
    private final FloodgateGateway gateway;
    private final BedrockUiSessionManager sessions;
    private final BedrockMenuRepository repository;

    public BedrockFormSupport(BedrockHost host, FloodgateGateway gateway, BedrockUiSessionManager sessions,
            BedrockMenuRepository repository) {
        this.host = host;
        this.gateway = gateway;
        this.sessions = sessions;
        this.repository = repository;
    }

    public BedrockHost host() {
        return host;
    }

    public boolean isBedrock(Player player) {
        return gateway.isBedrock(player);
    }

    /** Raw YAML of a MenusBedrock file, or an empty configuration. */
    public YamlConfiguration ui(String menuId) {
        YamlConfiguration ui = repository.rawMenu(menuId);
        return ui == null ? new YamlConfiguration() : ui;
    }

    public String color(String raw) {
        return host.color(raw);
    }

    public String strip(String text) {
        if (text == null)
            return "";
        return ChatColor.stripColor(host.color(text));
    }

    /** Renders target placeholders, {shop}/{discord} and colors. */
    public String text(String raw, Player player, UUID targetUuid, String targetName, boolean targetOnline) {
        if (raw == null)
            return "";
        String rendered = host.applyTargetPlaceholders(raw, player, targetUuid, targetName, targetOnline);
        String shopUrl = host.plugin().getConfig().getString("shop.url", "");
        String discordUrl = host.plugin().getConfig().getString("discord.url", "");
        return host.color(rendered.replace("{shop}", shopUrl).replace("{store_url}", shopUrl)
                .replace("{discord}", discordUrl).replace("{discord_url}", discordUrl));
    }

    public String text(String raw, Player player) {
        return text(raw, player, null, "", false);
    }

    public String uiText(YamlConfiguration ui, String path, String def, Player player, Map<String, String> tokens) {
        String raw = ui == null ? def : ui.getString(path, def);
        return host.color(host.applyPlayerPlaceholders(replaceTokens(raw, tokens), player));
    }

    public String uiLines(YamlConfiguration ui, String path, List<String> defaults, Player player,
            Map<String, String> tokens) {
        List<String> lines = ui == null ? Collections.emptyList() : ui.getStringList(path);
        if (lines.isEmpty())
            lines = defaults;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (out.length() > 0)
                out.append('\n');
            out.append(host.color(host.applyPlayerPlaceholders(replaceTokens(line, tokens), player)));
        }
        return out.toString();
    }

    public static String replaceTokens(String raw, Map<String, String> tokens) {
        String text = raw == null ? "" : raw;
        if (tokens != null) {
            for (Map.Entry<String, String> entry : tokens.entrySet())
                text = text.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return text;
    }

    /** Adds a button, with the optional image configured at {@code path.image}. */
    public void addButton(SimpleForm.Builder builder, YamlConfiguration ui, String path, String text) {
        String data = ui == null ? "" : ui.getString(path + ".image.data", "");
        if (data == null || data.isBlank()) {
            builder.button(text);
            return;
        }
        String type = ui.getString(path + ".image.type", "URL");
        builder.button(text, "PATH".equalsIgnoreCase(type) ? FormImage.Type.PATH : FormImage.Type.URL, data);
    }

    public int pageSize(Player player) {
        return sessions.dynamicPageSize(player);
    }

    public long begin(Player player) {
        return sessions.begin(player);
    }

    public void run(Player player, long session, Runnable action) {
        sessions.run(player, session, action);
    }

    public boolean send(Player player, Form form) {
        return gateway.send(player, form);
    }

    /**
     * Opens a SimpleForm whose buttons map 1:1 to {@code actions}. A new session
     * is started so late responses from a previous form are discarded.
     */
    public boolean show(Player player, SimpleForm.Builder builder, List<Runnable> actions) {
        long session = sessions.begin(player);
        builder.validResultHandler(response -> sessions.run(player, session, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        return gateway.send(player, builder.build());
    }

    public Player findOnlinePlayer(String rawName) {
        if (rawName == null || rawName.isBlank())
            return null;
        String name = rawName.trim();
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null)
            return exact;
        for (Player online : Bukkit.getOnlinePlayers())
            if (online.getName().equalsIgnoreCase(name))
                return online;
        return null;
    }
}

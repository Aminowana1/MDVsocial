package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Renders static MenusBedrock YAML files as native Cumulus SimpleForms.
 * YAML persistence/parsing lives in BedrockMenuRepository and response/session
 * serialization lives in BedrockUiSessionManager.
 */
final class BedrockMenuManager {

    private final MDVSocialPlugin plugin;
    private final BedrockMenuRepository repository;
    private boolean floodgateAvailable;

    BedrockMenuManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
        this.repository = new BedrockMenuRepository(plugin);
    }

    void enable() {
        reload();
    }

    void reload() {
        refreshFloodgateState();
        repository.reload();
        plugin.getLogger().info("Menus Bedrock cargados: " + repository.size()
                + (floodgateAvailable ? " (Floodgate detectado)" : " (Floodgate no detectado)"));
    }

    private void refreshFloodgateState() {
        floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    boolean isBedrock(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("bedrock.enabled", true))
            return false;
        if (!floodgateAvailable)
            refreshFloodgateState();
        if (!floodgateAvailable)
            return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean hasMenu(String menuId) {
        return repository.hasMenu(menuId);
    }

    YamlConfiguration rawMenu(String menuId) {
        return repository.rawMenu(menuId);
    }

    boolean open(Player player, String rawMenuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline) {
        if (!isBedrock(player))
            return false;

        String menuId = normalize(rawMenuId);
        BedrockMenuDefinition def = repository.menu(menuId);
        if (def == null)
            return false;

        if (!def.permission.isBlank() && !player.hasPermission(def.permission)) {
            plugin.sendNoPermission(player);
            return true;
        }

        int maxPage = def.maxPage();
        int safePage = Math.max(1, Math.min(page, maxPage));
        BedrockMenuContext context = new BedrockMenuContext(
                menuId,
                safePage,
                normalize(previousMenu),
                Math.max(1, previousPage),
                targetUuid,
                targetName == null ? "" : targetName,
                targetOnline);

        List<BedrockMenuButton> visible = new ArrayList<>();
        for (BedrockMenuButton button : def.pages.getOrDefault(safePage, Collections.emptyList())) {
            if (!button.isVisible(plugin, player, targetUuid, targetOnline))
                continue;
            if (!button.permission.isBlank() && !player.hasPermission(button.permission)
                    && button.hideWithoutPermission)
                continue;
            visible.add(button);
        }

        plugin.traceBedrockUi(player, "open menu=" + menuId + " page=" + safePage
                + " previous=" + context.previousMenu + " visible=" + visible.size());

        String title = plugin.bedrockText(def.title
                .replace("{page}", String.valueOf(safePage))
                .replace("{max_page}", String.valueOf(maxPage)),
                player, targetUuid, targetName, targetOnline);

        StringBuilder content = new StringBuilder();
        for (String line : def.content) {
            if (content.length() > 0)
                content.append('\n');
            content.append(plugin.bedrockText(line
                    .replace("{page}", String.valueOf(safePage))
                    .replace("{max_page}", String.valueOf(maxPage)),
                    player, targetUuid, targetName, targetOnline));
        }

        // Each form gets a unique token BEFORE its handler is registered. This is
        // important on mobile: a late result from the previous form can no longer
        // execute actions against the newly opened form/session.
        long session = plugin.beginBedrockUiSession(player);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(title)
                .content(content.toString());

        for (BedrockMenuButton button : visible) {
            String text = plugin.bedrockText(button.text, player, targetUuid, targetName, targetOnline);
            if (button.imageData.isBlank()) {
                builder.button(text);
            } else {
                FormImage.Type type = button.imageType.equalsIgnoreCase("PATH")
                        ? FormImage.Type.PATH
                        : FormImage.Type.URL;
                builder.button(text, type, button.imageData);
            }
        }

        if (visible.isEmpty())
            builder.button(plugin.bedrockText("&7Volver", player, targetUuid, targetName, targetOnline));

        builder.closedResultHandler(() -> {
            // Native X closes the form. We intentionally do not reopen anything.
        });
        builder.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (visible.isEmpty()) {
                plugin.traceBedrockUi(player, "click menu=" + menuId + " index=" + index + " fallback=BACK");
            } else if (index >= 0 && index < visible.size()) {
                BedrockMenuButton selected = visible.get(index);
                plugin.traceBedrockUi(player, "click menu=" + menuId + " index=" + index
                        + " button=" + selected.id + " action=" + selected.action
                        + " target=" + selected.targetMenu);
            } else {
                plugin.traceBedrockUi(player, "click invalido menu=" + menuId + " index=" + index
                        + " visible=" + visible.size());
            }

            plugin.runBedrockUiAction(player, session, () -> {
                if (!player.isOnline())
                    return;
                if (visible.isEmpty()) {
                    plugin.openBedrockBack(player, context);
                    return;
                }
                if (index < 0 || index >= visible.size())
                    return;
                plugin.handleBedrockMenuAction(player, visible.get(index), context);
            });
        });

        try {
            FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgatePlayer == null)
                return false;
            return floodgatePlayer.sendForm(builder.build());
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar el menu Bedrock " + menuId + " a "
                    + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null)
            return "";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}

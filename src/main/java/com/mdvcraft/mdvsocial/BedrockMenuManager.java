package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Renderizador nativo de menus para clientes Bedrock conectados por Floodgate.
 *
 * La logica de negocio sigue viviendo en MDVSocialPlugin. Esta clase solo:
 * - detecta Floodgate,
 * - carga plugins/MDVSocial/MenusBedrock/*.yml,
 * - construye SimpleForms,
 * - devuelve la accion elegida al core de MDVSocial.
 */
final class BedrockMenuManager {

    private static final List<String> DEFAULT_MENU_FILES = List.of(
            "main.yml",
            "menuperfil.yml",
            "menuamigos.yml",
            "jugador_opciones.yml",
            "amigo_opciones.yml",
            "correo.yml",
            "clan.yml",
            "clan_con_clan.yml",
            "clan_sin_clan.yml",
            "warps.yml",
            "ayuda.yml",
            "admin.yml",
            "amigos_lista.yml",
            "party.yml",
            "homes.yml",
            "titulos.yml",
            "titulos_lista.yml",
            "rangos.yml");

    private final MDVSocialPlugin plugin;
    private final Map<String, BedrockMenuDef> menus = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> rawMenus = new LinkedHashMap<>();
    private boolean floodgateAvailable;

    BedrockMenuManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        reload();
    }

    void reload() {
        refreshFloodgateState();
        ensureDefaultMenus();
        menus.clear();
        rawMenus.clear();

        File folder = new File(plugin.getDataFolder(), "MenusBedrock");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta MenusBedrock.");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> {
            String low = name.toLowerCase(Locale.ROOT);
            return low.endsWith(".yml") || low.endsWith(".yaml");
        });
        if (files == null)
            return;

        for (File file : files) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            String id = normalize(dot > 0 ? name.substring(0, dot) : name);
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                rawMenus.put(id, yaml);
                menus.put(id, parseMenu(id, yaml));
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo cargar menu Bedrock " + name + ": " + ex.getMessage());
            }
        }

        plugin.getLogger().info("Menus Bedrock cargados: " + menus.size()
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
        return menus.containsKey(normalize(menuId));
    }

    YamlConfiguration rawMenu(String menuId) {
        return rawMenus.get(normalize(menuId));
    }

    boolean open(Player player, String rawMenuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline) {
        if (!isBedrock(player))
            return false;

        String menuId = normalize(rawMenuId);
        BedrockMenuDef def = menus.get(menuId);
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

        if (visible.isEmpty()) {
            builder.button(plugin.bedrockText("&7Volver", player, targetUuid, targetName, targetOnline));
        }

        builder.closedResultHandler(() -> {
            // Cerrar un form Bedrock equivale a CLOSE. No reabrimos automaticamente.
        });
        builder.validResultHandler(response -> {
            int index = response.clickedButtonId();
            plugin.runBedrockUiAction(player, () -> {
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
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar el menu Bedrock " + menuId + " a "
                    + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private void ensureDefaultMenus() {
        File folder = new File(plugin.getDataFolder(), "MenusBedrock");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear MenusBedrock.");
            return;
        }
        for (String file : DEFAULT_MENU_FILES) {
            File target = new File(folder, file);
            if (!target.exists()) {
                try {
                    plugin.saveResource("MenusBedrock/" + file, false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("No existe el recurso MenusBedrock/" + file + " dentro del jar.");
                }
                continue;
            }
            mergeMissingMenuDefaults(target, file);
        }
    }

    /**
     * Añade solo claves nuevas a menus Bedrock existentes. Nunca reemplaza textos,
     * URLs ni valores que el administrador ya personalizó. Esto permite que una
     * actualización agregue botones nuevos (por ejemplo eliminar amigo/salir party)
     * sin obligar a reemplazar toda la carpeta MenusBedrock.
     */
    private void mergeMissingMenuDefaults(File target, String resourceName) {
        try (InputStream in = plugin.getResource("MenusBedrock/" + resourceName)) {
            if (in == null)
                return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(target);
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path))
                    continue;
                if (!current.contains(path)) {
                    current.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                current.save(target);
                plugin.getLogger().info("MenusBedrock/" + resourceName + " actualizado con nuevas claves.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo auto-actualizar MenusBedrock/" + resourceName
                    + ": " + ex.getMessage());
        }
    }

    private BedrockMenuDef parseMenu(String id, YamlConfiguration yaml) {
        String title = yaml.getString("title", "&l" + id);
        String permission = yaml.getString("permission", "");
        List<String> content = yaml.getStringList("content");
        if (content.isEmpty()) {
            String oneLine = yaml.getString("description", "&7Selecciona una opción.");
            content = List.of(oneLine);
        }

        BedrockMenuDef def = new BedrockMenuDef(id, title, permission, content);
        ConfigurationSection pages = yaml.getConfigurationSection("pages");
        if (pages != null) {
            for (String pageKey : pages.getKeys(false)) {
                int page = parsePage(pageKey);
                ConfigurationSection buttons = pages.getConfigurationSection(pageKey + ".buttons");
                if (buttons == null)
                    buttons = pages.getConfigurationSection(pageKey + ".items");
                if (buttons == null)
                    buttons = pages.getConfigurationSection(pageKey);
                loadButtons(def, page, buttons);
            }
        } else {
            ConfigurationSection buttons = yaml.getConfigurationSection("buttons");
            if (buttons == null)
                buttons = yaml.getConfigurationSection("items");
            loadButtons(def, 1, buttons);
        }
        if (def.pages.isEmpty())
            def.pages.put(1, new ArrayList<>());
        return def;
    }

    private void loadButtons(BedrockMenuDef def, int page, ConfigurationSection section) {
        List<BedrockMenuButton> list = def.pages.computeIfAbsent(page, ignored -> new ArrayList<>());
        if (section == null)
            return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(id);
            if (sec == null)
                continue;

            String text = sec.getString("text", sec.getString("name", "&f" + id));
            List<String> lore = sec.getStringList("lore");
            if (!lore.isEmpty() && !sec.contains("text")) {
                String shortLine = lore.stream()
                        .filter(line -> line != null && !line.isBlank())
                        .filter(line -> !line.toLowerCase(Locale.ROOT).contains("click"))
                        .findFirst().orElse("");
                if (!shortLine.isBlank())
                    text += "\n" + shortLine;
            }

            String action = plugin.normalizeBedrockAction(sec.getString("action", sec.getString("left-action", "")));
            // Desde 1.6.2 Bedrock no muestra botones explícitos de CERRAR.
            // Esto también filtra configs antiguas sin obligar al usuario a editarlas.
            if ("CLOSE".equals(action))
                continue;
            String targetMenu = normalize(sec.getString("target-menu", sec.getString("menu", "")));
            List<String> commands = new ArrayList<>(sec.getStringList("commands"));
            String single = sec.getString("command", "");
            if (commands.isEmpty() && !single.isBlank())
                commands.add(single);

            String imageType = sec.getString("image.type", sec.getString("image-type", ""));
            String imageData = sec.getString("image.data", sec.getString("image", ""));
            if (imageData != null && imageData.equalsIgnoreCase(imageType))
                imageData = "";

            list.add(new BedrockMenuButton(
                    id,
                    text,
                    action,
                    targetMenu,
                    commands,
                    sec.getBoolean("close-on-click", true),
                    sec.getString("visible-when", sec.getString("show-when", "always")),
                    sec.getString("condition-placeholder", sec.getString("placeholder", "")),
                    sec.getString("condition-equals", sec.getString("equals", "true")),
                    normalize(sec.getString("true-menu", sec.getString("menu-true", ""))),
                    normalize(sec.getString("false-menu", sec.getString("menu-false", ""))),
                    normalize(sec.getString("clans-menu", sec.getString("mdvclans-menu", targetMenu))),
                    sec.getString("sound", sec.getString("click-sound", "")),
                    sec.getString("permission", ""),
                    sec.getBoolean("hide-without-permission", sec.getBoolean("hide-no-permission", true)),
                    imageType == null ? "" : imageType,
                    imageData == null ? "" : imageData));
        }
    }

    private int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private String normalize(String value) {
        if (value == null)
            return "";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    static final class BedrockMenuContext {
        final String menuId;
        final int page;
        final String previousMenu;
        final int previousPage;
        final UUID targetUuid;
        final String targetName;
        final boolean targetOnline;

        BedrockMenuContext(String menuId, int page, String previousMenu, int previousPage,
                UUID targetUuid, String targetName, boolean targetOnline) {
            this.menuId = menuId == null ? "" : menuId;
            this.page = page;
            this.previousMenu = previousMenu == null ? "" : previousMenu;
            this.previousPage = previousPage;
            this.targetUuid = targetUuid;
            this.targetName = targetName == null ? "" : targetName;
            this.targetOnline = targetOnline;
        }
    }

    static final class BedrockMenuDef {
        final String id;
        final String title;
        final String permission;
        final List<String> content;
        final Map<Integer, List<BedrockMenuButton>> pages = new LinkedHashMap<>();

        BedrockMenuDef(String id, String title, String permission, List<String> content) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.permission = permission == null ? "" : permission.trim();
            this.content = content == null ? List.of() : content;
        }

        int maxPage() {
            if (pages.isEmpty())
                return 1;
            return pages.keySet().stream().max(Integer::compareTo).orElse(1);
        }
    }

    static final class BedrockMenuButton {
        final String id;
        final String text;
        final String action;
        final String targetMenu;
        final List<String> commands;
        final boolean closeOnClick;
        final String visibleWhen;
        final String conditionPlaceholder;
        final String conditionEquals;
        final String trueMenu;
        final String falseMenu;
        final String clansMenu;
        final String sound;
        final String permission;
        final boolean hideWithoutPermission;
        final String imageType;
        final String imageData;

        BedrockMenuButton(String id, String text, String action, String targetMenu, List<String> commands,
                boolean closeOnClick, String visibleWhen, String conditionPlaceholder,
                String conditionEquals, String trueMenu, String falseMenu, String clansMenu,
                String sound, String permission, boolean hideWithoutPermission,
                String imageType, String imageData) {
            this.id = id == null ? "" : id;
            this.text = text == null ? "" : text;
            this.action = action == null ? "" : action;
            this.targetMenu = targetMenu == null ? "" : targetMenu;
            this.commands = commands == null ? List.of() : commands;
            this.closeOnClick = closeOnClick;
            this.visibleWhen = visibleWhen == null ? "always"
                    : visibleWhen.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            this.conditionPlaceholder = conditionPlaceholder == null ? "" : conditionPlaceholder;
            this.conditionEquals = conditionEquals == null ? "true" : conditionEquals;
            this.trueMenu = trueMenu == null ? "" : trueMenu;
            this.falseMenu = falseMenu == null ? "" : falseMenu;
            this.clansMenu = clansMenu == null ? "" : clansMenu;
            this.sound = sound == null ? "" : sound;
            this.permission = permission == null ? "" : permission.trim();
            this.hideWithoutPermission = hideWithoutPermission;
            this.imageType = imageType == null ? "" : imageType;
            this.imageData = imageData == null ? "" : imageData;
        }

        boolean isVisible(MDVSocialPlugin plugin, Player viewer, UUID targetUuid, boolean targetOnline) {
            boolean hasTarget = targetUuid != null;
            boolean self = hasTarget && viewer.getUniqueId().equals(targetUuid);
            boolean friend = hasTarget && plugin.isBedrockFriend(viewer, targetUuid);
            return switch (visibleWhen) {
                case "online", "target_online", "friend_online" -> hasTarget && targetOnline;
                case "offline", "target_offline", "friend_offline" -> hasTarget && !targetOnline;
                case "target", "has_target" -> hasTarget;
                case "friend", "is_friend", "already_friend" -> hasTarget && friend;
                case "not_friend", "non_friend" -> hasTarget && !friend && !self;
                case "online_friend" -> hasTarget && targetOnline && friend;
                case "online_not_friend", "online_non_friend" -> hasTarget && targetOnline && !friend && !self;
                case "offline_not_friend", "offline_non_friend" -> hasTarget && !targetOnline && !friend && !self;
                case "self" -> self;
                case "not_self", "other" -> hasTarget && !self;
                case "online_not_self", "online_other" -> hasTarget && targetOnline && !self;
                default -> true;
            };
        }
    }
}

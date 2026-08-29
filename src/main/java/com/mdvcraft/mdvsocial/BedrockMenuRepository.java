package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns loading, parsing and safe auto-updating of MenusBedrock YAML files.
 * Rendering and player navigation intentionally live elsewhere.
 */
final class BedrockMenuRepository {

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
    private final Map<String, BedrockMenuDefinition> menus = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> rawMenus = new LinkedHashMap<>();

    BedrockMenuRepository(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void reload() {
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
    }

    int size() {
        return menus.size();
    }

    boolean hasMenu(String menuId) {
        return menus.containsKey(normalize(menuId));
    }

    BedrockMenuDefinition menu(String menuId) {
        return menus.get(normalize(menuId));
    }

    YamlConfiguration rawMenu(String menuId) {
        return rawMenus.get(normalize(menuId));
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
     * Adds only missing scalar/list values. Existing administrator customizations
     * (texts, URLs, images and actions) are never overwritten.
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

    private BedrockMenuDefinition parseMenu(String id, YamlConfiguration yaml) {
        String title = yaml.getString("title", "&l" + id);
        String permission = yaml.getString("permission", "");
        List<String> content = yaml.getStringList("content");
        if (content.isEmpty()) {
            String oneLine = yaml.getString("description", "&7Selecciona una opción.");
            content = List.of(oneLine);
        }

        BedrockMenuDefinition def = new BedrockMenuDefinition(id, title, permission, content);
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

    private void loadButtons(BedrockMenuDefinition def, int page, ConfigurationSection section) {
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
            // Bedrock uses the native X to close forms; explicit CLOSE buttons are hidden.
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
}

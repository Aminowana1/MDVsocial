package com.mdvcraft.mdvsocial.bedrock.menu;

import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Owns loading and safe auto-updating of MenusBedrock YAML files.
 * Parsing lives in BedrockMenuParser and rendering in BedrockMenuRenderer.
 *
 * Menus are discovered recursively, so administrators can group them in
 * subfolders (e.g. MenusBedrock/clan/clan.yml). The menu id is always the file
 * name without extension; when two files share an id the shallowest wins.
 */
public final class BedrockMenuRepository {

    public static final String FOLDER = "MenusBedrock";

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
            "mmocore_perfil.yml",
            "mmocore_atributos.yml",
            "mmocore_clases.yml",
            "homes.yml",
            "tienda.yml",
            "discord.yml",
            "titulos.yml",
            "titulos_lista.yml",
            "rangos.yml",
            "bloqueados.yml");

    private final JavaPlugin plugin;
    private final BedrockMenuParser parser;
    private final Map<String, BedrockMenuDefinition> menus = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> rawMenus = new LinkedHashMap<>();

    public BedrockMenuRepository(JavaPlugin plugin, BedrockMenuParser parser) {
        this.plugin = plugin;
        this.parser = parser;
    }

    public void reload() {
        ensureDefaultMenus();
        menus.clear();
        rawMenus.clear();

        File folder = new File(plugin.getDataFolder(), FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta " + FOLDER + ".");
            return;
        }

        for (Path file : menuFiles(folder.toPath())) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String id = BedrockMenuParser.normalize(dot > 0 ? name.substring(0, dot) : name);
            if (rawMenus.containsKey(id)) {
                plugin.getLogger().warning("Menu Bedrock duplicado '" + id + "' en "
                        + folder.toPath().relativize(file) + "; se ignora.");
                continue;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                if ("protections".equalsIgnoreCase(yaml.getString("managed-by", "")))
                    continue;
                rawMenus.put(id, yaml);
                menus.put(id, parser.parse(id, yaml));
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo cargar menu Bedrock " + name + ": " + ex.getMessage());
            }
        }
    }

    public int size() {
        return menus.size();
    }

    public boolean hasMenu(String menuId) {
        return menus.containsKey(BedrockMenuParser.normalize(menuId));
    }

    public BedrockMenuDefinition menu(String menuId) {
        return menus.get(BedrockMenuParser.normalize(menuId));
    }

    public YamlConfiguration rawMenu(String menuId) {
        return rawMenus.get(BedrockMenuParser.normalize(menuId));
    }

    /** YAML files under the folder, shallowest first and then alphabetically. */
    static List<Path> menuFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> {
                        String low = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return low.endsWith(".yml") || low.endsWith(".yaml");
                    })
                    .sorted(Comparator.<Path>comparingInt(path -> root.relativize(path).getNameCount())
                            .thenComparing(path -> root.relativize(path).toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void ensureDefaultMenus() {
        File folder = new File(plugin.getDataFolder(), FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear " + FOLDER + ".");
            return;
        }
        Map<String, File> existing = new LinkedHashMap<>();
        for (Path path : menuFiles(folder.toPath()))
            existing.putIfAbsent(path.getFileName().toString().toLowerCase(Locale.ROOT), path.toFile());

        for (String file : DEFAULT_MENU_FILES) {
            // A default menu moved into a subfolder by an administrator is updated in place.
            File target = existing.get(file.toLowerCase(Locale.ROOT));
            if (target == null) {
                try {
                    plugin.saveResource(FOLDER + "/" + file, false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("No existe el recurso " + FOLDER + "/" + file + " dentro del jar.");
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
        try (InputStream in = plugin.getResource(FOLDER + "/" + resourceName)) {
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
                plugin.getLogger().info(FOLDER + "/" + resourceName + " actualizado con nuevas claves.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo auto-actualizar " + FOLDER + "/" + resourceName
                    + ": " + ex.getMessage());
        }
    }
}

package com.mdvcraft.mdvsocial;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warns about top-level sections written twice in config.yml. YAML keeps only
 * the last one, so values edited in the first copy are silently ignored (this
 * happened with {@code scoreboard}).
 */
final class ConfigDuplicateKeys {

    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*:");

    private ConfigDuplicateKeys() {
    }

    static void warn(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile())
            return;
        try {
            for (Map.Entry<String, List<Integer>> entry
                    : find(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)).entrySet()) {
                List<Integer> lines = entry.getValue();
                plugin.getLogger().warning("config.yml tiene la sección '" + entry.getKey() + "' repetida (líneas "
                        + lines + "). Solo se usa la última (línea " + lines.get(lines.size() - 1)
                        + "); une las copias en una sola.");
            }
        } catch (IOException ex) {
            plugin.getLogger().fine("No se pudo revisar config.yml: " + ex.getMessage());
        }
    }

    /** Top-level keys that appear more than once, with their 1-based line numbers. */
    static Map<String, List<Integer>> find(List<String> lines) {
        Map<String, List<Integer>> seen = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = TOP_LEVEL_KEY.matcher(lines.get(i));
            if (matcher.find())
                seen.computeIfAbsent(matcher.group(1), key -> new ArrayList<>()).add(i + 1);
        }
        seen.values().removeIf(found -> found.size() < 2);
        return seen;
    }
}

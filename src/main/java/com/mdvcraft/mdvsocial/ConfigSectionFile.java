package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A config section that lives in its own file (titles.yml, ranks.yml) instead
 * of config.yml. The entries are the top-level keys of the file.
 *
 * The first time the file is missing, a section with the same name still in
 * the server's config.yml is moved there as text, so comments and formatting
 * are kept; config.yml is backed up before it is changed. Without such a
 * section the bundled default file is created.
 */
final class ConfigSectionFile {

    /** A top-level block of config.yml: its lines and the comment lines right above it. */
    record Block(int start, int end, List<String> header, List<String> body) {
    }

    private ConfigSectionFile() {
    }

    static YamlConfiguration load(JavaPlugin plugin, String fileName, String legacySection) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists() && !moveFromConfig(plugin, file, legacySection))
            plugin.saveResource(fileName, false);
        return YamlConfiguration.loadConfiguration(file);
    }

    private static boolean moveFromConfig(JavaPlugin plugin, File target, String section) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.isFile())
            return false;
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            Block block = findBlock(lines, section);
            if (block == null)
                return false;
            YamlConfiguration original = YamlConfiguration.loadConfiguration(configFile);
            String moved = movedText(block, original.getConfigurationSection(section));
            Files.writeString(target.toPath(), moved, StandardCharsets.UTF_8);

            File backup = new File(plugin.getDataFolder(), "config.yml.bak-" + section);
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            List<String> remaining = new ArrayList<>(lines.subList(0, block.start()));
            remaining.add("# '" + section + "' se movió a " + target.getName() + ".");
            remaining.addAll(lines.subList(block.end(), lines.size()));
            Files.write(configFile.toPath(), remaining, StandardCharsets.UTF_8);
            plugin.getLogger().info("Sección '" + section + "' movida de config.yml a " + target.getName()
                    + " (copia de seguridad: " + backup.getName() + ").");
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo mover '" + section + "' a " + target.getName() + ": "
                    + ex.getMessage());
            return false;
        }
    }

    /**
     * The block as its own file: comments above it plus its body one level less
     * indented. Falls back to a plain dump if the text would not read back the same.
     */
    static String movedText(Block block, ConfigurationSection section) {
        List<String> out = new ArrayList<>(block.header());
        out.addAll(block.body());
        String text = String.join("\n", out) + "\n";
        if (section == null || sameValues(text, section))
            return text;
        YamlConfiguration dump = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : section.getValues(true).entrySet())
            if (!(entry.getValue() instanceof ConfigurationSection))
                dump.set(entry.getKey(), entry.getValue());
        return dump.saveToString();
    }

    private static boolean sameValues(String text, ConfigurationSection expected) {
        YamlConfiguration parsed = new YamlConfiguration();
        try {
            parsed.loadFromString(text);
        } catch (InvalidConfigurationException ex) {
            return false;
        }
        Map<String, Object> want = expected.getValues(true);
        Map<String, Object> got = parsed.getValues(true);
        if (!want.keySet().equals(got.keySet()))
            return false;
        for (Map.Entry<String, Object> entry : want.entrySet())
            if (!(entry.getValue() instanceof ConfigurationSection)
                    && !Objects.equals(entry.getValue(), got.get(entry.getKey())))
                return false;
        return true;
    }

    /**
     * Finds {@code key:} at column 0 and its indented body, up to the next line
     * that starts at column 0. The comment lines just above belong to it.
     *
     * @return null when the key is missing or has an inline value
     */
    static Block findBlock(List<String> lines, String key) {
        int keyLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(key + ":") && line.substring(key.length() + 1).replaceAll("#.*", "").isBlank()) {
                keyLine = i;
                break;
            }
        }
        if (keyLine < 0)
            return null;

        int end = keyLine + 1;
        while (end < lines.size() && (lines.get(end).isBlank() || Character.isWhitespace(lines.get(end).charAt(0))))
            end++;
        // Trailing blank lines stay in config.yml as separation.
        while (end > keyLine + 1 && lines.get(end - 1).isBlank())
            end--;

        // Comments above it (blank lines between them included) describe it too.
        int start = keyLine;
        while (start > 0 && (lines.get(start - 1).startsWith("#") || lines.get(start - 1).isBlank()))
            start--;
        while (start < keyLine && lines.get(start).isBlank())
            start++;

        List<String> body = dedent(lines.subList(keyLine + 1, end));
        return new Block(start, end, List.copyOf(lines.subList(start, keyLine)), body);
    }

    private static List<String> dedent(List<String> lines) {
        int indent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank() || line.trim().startsWith("#"))
                continue;
            indent = Math.min(indent, line.length() - line.stripLeading().length());
        }
        if (indent == Integer.MAX_VALUE)
            indent = 0;
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String stripped = line.stripTrailing();
            int leading = stripped.length() - stripped.stripLeading().length();
            out.add(stripped.substring(Math.min(indent, leading)));
        }
        return out;
    }
}

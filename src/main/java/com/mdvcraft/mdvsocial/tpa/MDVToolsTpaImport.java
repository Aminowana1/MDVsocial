package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

/**
 * One-time move of the TPA settings from plugins/MDVTools/config.yml, which no
 * longer handles teleports. Runs only while MDVSocial's config.yml has no
 * {@code tpa} section; the section is appended so existing comments stay.
 */
final class MDVToolsTpaImport {

    private static final Pattern OLD_TOKEN = Pattern.compile("%([a-z_]+)%");

    /** Replaced by the configurable notice (tpa.notice.java.format). */
    private static final String DROPPED_MESSAGE = "messages.request-received";

    private MDVToolsTpaImport() {
    }

    /** @return true when settings were imported (the caller should reload the config). */
    static boolean run(JavaPlugin plugin) {
        File ownFile = new File(plugin.getDataFolder(), "config.yml");
        File oldFile = new File(plugin.getDataFolder().getParentFile(), "MDVTools/config.yml");
        if (!ownFile.isFile() || !oldFile.isFile())
            return false;
        if (YamlConfiguration.loadConfiguration(ownFile).isConfigurationSection("tpa"))
            return false;
        YamlConfiguration imported = convert(YamlConfiguration.loadConfiguration(oldFile));
        if (imported == null)
            return false;
        try {
            String block = "\n# ==========================================================\n"
                    + "# TPA (importado automáticamente desde MDVTools/config.yml)\n"
                    + "# Los avisos con botones están en tpa.notice; ver docs/TPA.md.\n"
                    + "# ==========================================================\n"
                    + imported.saveToString();
            Files.writeString(ownFile.toPath(), block, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            plugin.getLogger().info("Configuración de TPA importada desde MDVTools/config.yml.");
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo importar la configuración de TPA de MDVTools: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Builds the {@code tpa} section for MDVSocial from an MDVTools config:
     * same keys, {@code %token%} turned into {@code {token}}, and the MDVTools
     * prefix kept as {@code tpa.prefix}.
     *
     * @return null when the MDVTools config has no tpa section
     */
    static YamlConfiguration convert(ConfigurationSection mdvTools) {
        ConfigurationSection old = mdvTools.getConfigurationSection("tpa");
        if (old == null)
            return null;
        YamlConfiguration out = new YamlConfiguration();
        String prefix = mdvTools.getString("messages.prefix");
        if (prefix != null)
            out.set("tpa.prefix", prefix);
        for (String path : old.getKeys(true)) {
            if (old.isConfigurationSection(path) || path.equals(DROPPED_MESSAGE))
                continue;
            Object value = old.get(path);
            out.set("tpa." + path, value instanceof String text ? OLD_TOKEN.matcher(text).replaceAll("{$1}") : value);
        }
        return out;
    }
}

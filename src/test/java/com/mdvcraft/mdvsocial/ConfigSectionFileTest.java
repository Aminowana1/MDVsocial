package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigSectionFileTest {

    private static final List<String> CONFIG = List.of(
            "shop:",
            "  enabled: true",
            "",
            "# ===",
            "# Titulos",
            "",
            "titles:",
            "  noble:",
            "    display: '&6Noble'",
            "    # comentario interno",
            "    lore:",
            "      - '&7Linea'",
            "",
            "  forastero:",
            "    hidden: true",
            "",
            "# Tienda",
            "discord: 'x'");

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void blockKeepsItsCommentsAndStopsAtTheNextSection() {
        ConfigSectionFile.Block block = ConfigSectionFile.findBlock(CONFIG, "titles");
        assertEquals(3, block.start(), "comments above, across a blank line");
        assertEquals(15, block.end(), "trailing blank line and the next section's comment stay");
        assertEquals(List.of("# ===", "# Titulos", ""), block.header());
        assertEquals("noble:", block.body().get(0));
        assertEquals("  # comentario interno", block.body().get(2));
    }

    @Test
    void movedTextReadsBackTheSameValues() throws Exception {
        YamlConfiguration config = yaml(String.join("\n", CONFIG));
        ConfigSectionFile.Block block = ConfigSectionFile.findBlock(CONFIG, "titles");
        YamlConfiguration moved = yaml(ConfigSectionFile.movedText(block, config.getConfigurationSection("titles")));
        assertEquals("&6Noble", moved.getString("noble.display"));
        assertEquals(List.of("&7Linea"), moved.getStringList("noble.lore"));
        assertTrue(moved.getBoolean("forastero.hidden"));

        List<String> rest = new ArrayList<>(CONFIG.subList(0, block.start()));
        rest.addAll(CONFIG.subList(block.end(), CONFIG.size()));
        YamlConfiguration remaining = yaml(String.join("\n", rest));
        assertFalse(remaining.contains("titles"));
        assertEquals("x", remaining.getString("discord"));
    }

    @Test
    void sectionIsMovedOutOfTheServerConfig(@TempDir Path folder) throws Exception {
        Files.write(folder.resolve("config.yml"), CONFIG, StandardCharsets.UTF_8);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());

        YamlConfiguration titles = ConfigSectionFile.load(plugin, "titles.yml", "titles");

        assertEquals("&6Noble", titles.getString("noble.display"));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile());
        assertFalse(config.contains("titles"));
        assertTrue(config.getBoolean("shop.enabled"));
        assertTrue(Files.exists(folder.resolve("config.yml.bak-titles")));
        verify(plugin, never()).saveResource(anyString(), anyBoolean());

        // Second start: the file exists, nothing is moved or created again.
        assertEquals("&6Noble", ConfigSectionFile.load(plugin, "titles.yml", "titles").getString("noble.display"));
    }

    @Test
    void missingOrInlineSectionIsNotMoved() {
        assertNull(ConfigSectionFile.findBlock(CONFIG, "ranks"));
        assertNull(ConfigSectionFile.findBlock(List.of("titles: {}"), "titles"));
    }

    @Test
    void bundledFilesHoldTheEntries() {
        YamlConfiguration config = bundled("config.yml");
        assertFalse(config.contains("titles"));
        assertFalse(config.contains("ranks"));
        assertTrue(bundled("titles.yml").isConfigurationSection("forastero"));
        assertTrue(bundled("ranks.yml").isConfigurationSection("aventurero"));
    }

    private static YamlConfiguration bundled(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                ConfigSectionFileTest.class.getClassLoader().getResourceAsStream(name), StandardCharsets.UTF_8));
    }
}

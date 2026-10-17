package com.mdvcraft.mdvsocial.bedrock.menu;

import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuButton;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Turns a MenusBedrock YAML file into a {@link BedrockMenuDefinition}. */
public final class BedrockMenuParser {

    private final UnaryOperator<String> actionNormalizer;

    public BedrockMenuParser(UnaryOperator<String> actionNormalizer) {
        this.actionNormalizer = actionNormalizer;
    }

    public BedrockMenuDefinition parse(String id, YamlConfiguration yaml) {
        String title = yaml.getString("title", "&l" + id);
        String permission = yaml.getString("permission", "");
        List<String> content = yaml.getStringList("content");
        if (content.isEmpty()) {
            String oneLine = yaml.getString("description", "&7Selecciona una opción.");
            content = List.of(oneLine);
        }

        Map<Integer, List<BedrockMenuButton>> pages = new LinkedHashMap<>();
        ConfigurationSection pagesSection = yaml.getConfigurationSection("pages");
        if (pagesSection != null) {
            for (String pageKey : pagesSection.getKeys(false)) {
                int page = parsePage(pageKey);
                ConfigurationSection buttons = pagesSection.getConfigurationSection(pageKey + ".buttons");
                if (buttons == null)
                    buttons = pagesSection.getConfigurationSection(pageKey + ".items");
                if (buttons == null)
                    buttons = pagesSection.getConfigurationSection(pageKey);
                loadButtons(pages.computeIfAbsent(page, ignored -> new ArrayList<>()), buttons);
            }
        } else {
            ConfigurationSection buttons = yaml.getConfigurationSection("buttons");
            if (buttons == null)
                buttons = yaml.getConfigurationSection("items");
            loadButtons(pages.computeIfAbsent(1, ignored -> new ArrayList<>()), buttons);
        }
        return new BedrockMenuDefinition(id, title, permission, content, pages);
    }

    private void loadButtons(List<BedrockMenuButton> list, ConfigurationSection section) {
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

            String action = actionNormalizer.apply(sec.getString("action", sec.getString("left-action", "")));
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
                    imageType,
                    imageData));
        }
    }

    private int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static String normalize(String value) {
        if (value == null)
            return "";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}

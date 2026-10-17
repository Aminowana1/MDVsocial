package com.mdvcraft.mdvsocial.bedrock.menu.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed static YAML form definition from MenusBedrock. */
public final class BedrockMenuDefinition {
    private final String id;
    private final String title;
    private final String permission;
    private final List<String> content;
    private final Map<Integer, List<BedrockMenuButton>> pages;

    public BedrockMenuDefinition(String id, String title, String permission, List<String> content,
            Map<Integer, List<BedrockMenuButton>> pages) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.permission = permission == null ? "" : permission.trim();
        this.content = content == null ? List.of() : List.copyOf(content);
        Map<Integer, List<BedrockMenuButton>> copy = new LinkedHashMap<>();
        if (pages != null)
            pages.forEach((page, buttons) -> copy.put(page, List.copyOf(buttons)));
        if (copy.isEmpty())
            copy.put(1, List.of());
        this.pages = Collections.unmodifiableMap(copy);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String permission() {
        return permission;
    }

    public List<String> content() {
        return content;
    }

    public Map<Integer, List<BedrockMenuButton>> pages() {
        return pages;
    }

    public int maxPage() {
        return pages.keySet().stream().max(Integer::compareTo).orElse(1);
    }

    public List<BedrockMenuButton> page(int page) {
        return pages.getOrDefault(page, List.of());
    }
}

package com.mdvcraft.mdvsocial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed static YAML form definition from MenusBedrock. */
final class BedrockMenuDefinition {
    final String id;
    final String title;
    final String permission;
    final List<String> content;
    final Map<Integer, List<BedrockMenuButton>> pages = new LinkedHashMap<>();

    BedrockMenuDefinition(String id, String title, String permission, List<String> content) {
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

    List<BedrockMenuButton> page(int page) {
        return pages.getOrDefault(page, new ArrayList<>());
    }
}

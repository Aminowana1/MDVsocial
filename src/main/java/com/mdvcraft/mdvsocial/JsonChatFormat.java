package com.mdvcraft.mdvsocial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns configured chat lines into components. Each line is either raw JSON
 * text ({"text":...} or [...], with clickEvent/hoverEvent) or plain text; in
 * both cases &amp; color codes are allowed inside the texts and hover texts.
 */
public final class JsonChatFormat {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private JsonChatFormat() {
    }

    public static List<Component> render(List<String> lines, Map<String, String> tokens) {
        List<Component> out = new ArrayList<>();
        for (String line : lines)
            out.add(renderLine(replace(line, tokens)));
        return out;
    }

    static Component renderLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return colorize(GsonComponentSerializer.gson().deserialize(trimmed));
            } catch (Exception ignored) {
                // Not valid JSON: show it as plain text below rather than failing.
            }
        }
        return LEGACY.deserialize(line == null ? "" : line);
    }

    /** Applies &amp; codes inside every text node and show_text hover, keeping click/hover events. */
    static Component colorize(Component component) {
        Component result = component;
        if (component instanceof TextComponent text && text.content().indexOf('&') >= 0) {
            result = Component.text().style(text.style()).append(LEGACY.deserialize(text.content())).build();
        } else {
            result = result.children(List.of());
        }
        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT)
            result = result.hoverEvent(HoverEvent.showText(colorize((Component) hover.value())));
        for (Component child : component.children())
            result = result.append(colorize(child));
        return result;
    }

    private static String replace(String raw, Map<String, String> tokens) {
        String text = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : tokens.entrySet())
            text = text.replace("{" + entry.getKey() + "}", jsonSafe(entry.getValue()));
        return text;
    }

    /** Token values are inserted into JSON strings, so quotes and backslashes are escaped. */
    private static String jsonSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

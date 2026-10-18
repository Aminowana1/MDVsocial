package com.mdvcraft.mdvsocial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonChatFormatTest {

    private static final List<String> FORMAT = List.of(
            "{\"text\":\"\"}",
            "{\"text\":\"&6&l[&5&lMDVCRAFT&6&l]  &4»&r &6{player} &ete ha invitado a su grupo.\"}",
            "[{\"text\":\"        \"},{\"text\":\"&8[&a&lACEPTAR&8]\",\"clickEvent\":{\"action\":\"run_command\","
                    + "\"value\":\"/mmocore:party accept {uuid}\"},\"hoverEvent\":{\"action\":\"show_text\","
                    + "\"value\":\"&eClick para aceptar.\"}},{\"text\":\"&r             \"},{\"text\":\"&8[&c&lRECHAZAR&8]\","
                    + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/mmocore:party deny {uuid}\"},"
                    + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"&eClick para rechazar.\"}}]",
            "{\"text\":\"\"}");

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<Component> flatten(Component component) {
        List<Component> out = new ArrayList<>();
        out.add(component);
        for (Component child : component.children())
            out.addAll(flatten(child));
        return out;
    }

    @Test
    void rendersExampleFormatWithColorsAndTokens() {
        List<Component> lines = JsonChatFormat.render(FORMAT, Map.of("player", "Steve", "uuid", "1234"));
        assertEquals(4, lines.size());
        assertEquals("", plain(lines.get(0)));
        assertEquals("[MDVCRAFT]  » Steve te ha invitado a su grupo.", plain(lines.get(1)));
        assertTrue(plain(lines.get(2)).contains("[ACEPTAR]") && plain(lines.get(2)).contains("[RECHAZAR]"));
    }

    @Test
    void keepsClickAndHoverEvents() {
        Component buttons = JsonChatFormat.render(FORMAT, Map.of("player", "Steve", "uuid", "1234")).get(2);
        List<String> commands = flatten(buttons).stream()
                .map(Component::clickEvent).filter(e -> e != null && e.action() == ClickEvent.Action.RUN_COMMAND)
                .map(ClickEvent::value).distinct().toList();
        assertEquals(List.of("/mmocore:party accept 1234", "/mmocore:party deny 1234"), commands);

        Component hover = flatten(buttons).stream().map(Component::hoverEvent).filter(h -> h != null)
                .map(h -> (Component) h.value()).findFirst().orElseThrow();
        assertEquals("Click para aceptar.", plain(hover));
        assertTrue(flatten(hover).stream().anyMatch(c -> NamedTextColor.YELLOW.equals(c.color())));
    }

    @Test
    void plainLinesAndBadJsonFallBackToLegacyText() {
        assertEquals("Hola Steve", plain(JsonChatFormat.render(List.of("&aHola {player}"), Map.of("player", "Steve"))
                .get(0)));
        assertEquals("{roto", plain(JsonChatFormat.renderLine("{roto")));
    }

    @Test
    void tokenValuesCannotBreakTheJson() {
        Component line = JsonChatFormat.render(List.of("{\"text\":\"{player}\"}"), Map.of("player", "a\"b")).get(0);
        assertEquals("a\"b", plain(line));
    }
}

package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled config.yml must produce working invite messages. */
class PartyInviteConfigTest {

    private static YamlConfiguration bundledConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                PartyInviteConfigTest.class.getClassLoader().getResourceAsStream("config.yml"),
                StandardCharsets.UTF_8));
    }

    private static List<Component> flatten(Component component) {
        List<Component> out = new ArrayList<>();
        out.add(component);
        for (Component child : component.children())
            out.addAll(flatten(child));
        return out;
    }

    @Test
    void javaFormatHasAcceptAndDenyButtons() {
        List<String> format = bundledConfig().getStringList("party-invites.java.format");
        assertEquals(4, format.size());
        List<String> commands = JsonChatFormat.render(format, Map.of("player", "Steve", "uuid", "u-1")).stream()
                .flatMap(line -> flatten(line).stream())
                .map(Component::clickEvent)
                .filter(e -> e != null && e.action() == ClickEvent.Action.RUN_COMMAND)
                .map(ClickEvent::value).distinct().toList();
        assertEquals(List.of("/mmocore:party accept u-1", "/mmocore:party deny u-1"), commands);
    }

    @Test
    void tpaJavaNoticeHasAcceptAndDenyButtons() {
        YamlConfiguration config = bundledConfig();
        List<String> commands = JsonChatFormat.render(config.getStringList("tpa.notice.java.format"),
                        Map.of("player", "Steve", "uuid", "u-1", "seconds", "60")).stream()
                .flatMap(line -> flatten(line).stream())
                .map(Component::clickEvent)
                .filter(e -> e != null && e.action() == ClickEvent.Action.RUN_COMMAND)
                .map(ClickEvent::value).distinct().toList();
        assertEquals(List.of("/tpa aceptarsolicitud u-1", "/tpa rechazarsolicitud u-1"), commands);
        assertFalse(config.getStringList("tpa.notice.bedrock.message").isEmpty());
        assertTrue(QuickAcceptSettings.from(config.getConfigurationSection("quick-accept"), "tpa").enabled());
    }

    @Test
    void bedrockMessagesAndQuickAcceptDefaults() {
        YamlConfiguration config = bundledConfig();
        assertFalse(config.getStringList("party-invites.bedrock.message").isEmpty());
        assertFalse(config.getStringList("party-invites.bedrock.message-without-quick-accept").isEmpty());
        assertTrue(config.getBoolean("party-invites.bedrock.skip-confirmation-for-friends"));

        QuickAcceptSettings settings = QuickAcceptSettings.from(config.getConfigurationSection("quick-accept"),
                PartyInviteNotifier.QUICK_ACCEPT_TYPE);
        assertTrue(settings.enabled());
        assertTrue(settings.bedrockOnly());
        assertEquals(2, settings.sneaksRequired());
        assertEquals(30, settings.windowSeconds());
    }
}

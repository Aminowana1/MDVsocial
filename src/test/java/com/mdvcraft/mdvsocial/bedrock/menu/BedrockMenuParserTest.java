package com.mdvcraft.mdvsocial.bedrock.menu;

import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuButton;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuDefinition;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockMenuParserTest {

    private final BedrockMenuParser parser = new BedrockMenuParser(a -> a == null ? "" : a.toUpperCase(Locale.ROOT));

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void parsesSinglePageMenu() throws Exception {
        BedrockMenuDefinition def = parser.parse("main", yaml("""
                title: '&lPrincipal'
                permission: ' mdv.menu '
                content: ['linea 1', 'linea 2']
                buttons:
                  amigos:
                    text: 'Amigos'
                    action: open_menu
                    target-menu: Menu-Amigos
                    visible-when: Online-Friend
                    image:
                      type: PATH
                      data: textures/ui/icon.png
                  cerrar:
                    text: 'Cerrar'
                    action: close
                """));

        assertEquals("&lPrincipal", def.title());
        assertEquals("mdv.menu", def.permission());
        assertEquals(List.of("linea 1", "linea 2"), def.content());
        assertEquals(1, def.maxPage());

        List<BedrockMenuButton> buttons = def.page(1);
        assertEquals(1, buttons.size(), "CLOSE buttons are hidden on Bedrock");
        BedrockMenuButton amigos = buttons.get(0);
        assertEquals("OPEN_MENU", amigos.action());
        assertEquals("menu_amigos", amigos.targetMenu());
        assertEquals("online_friend", amigos.visibleWhen());
        assertEquals("PATH", amigos.imageType());
        assertEquals("textures/ui/icon.png", amigos.imageData());
        assertTrue(amigos.hasImage());
    }

    @Test
    void parsesPagesLoreAndSingleCommand() throws Exception {
        BedrockMenuDefinition def = parser.parse("ayuda", yaml("""
                pages:
                  '1':
                    buttons:
                      a:
                        name: 'Nombre'
                        lore: ['', 'Click para abrir', 'Detalle']
                        action: command_player
                        command: '/spawn'
                  '3':
                    items:
                      b:
                        text: 'B'
                        image: URL
                        image-type: URL
                """));

        assertEquals(3, def.maxPage());
        BedrockMenuButton a = def.page(1).get(0);
        assertEquals("Nombre\nDetalle", a.text(), "first non-click lore line is appended");
        assertEquals(List.of("/spawn"), a.commands());

        BedrockMenuButton b = def.page(3).get(0);
        assertEquals("", b.imageData(), "image equal to its type is treated as missing");
        assertTrue(def.page(2).isEmpty());
    }

    @Test
    void defaultsWhenContentMissing() throws Exception {
        BedrockMenuDefinition def = parser.parse("vacio", yaml("description: 'Solo una linea'"));
        assertEquals("&lvacio", def.title());
        assertEquals(List.of("Solo una linea"), def.content());
        assertEquals(1, def.maxPage());
        assertTrue(def.page(1).isEmpty());
    }
}

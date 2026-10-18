package com.mdvcraft.mdvsocial.bedrock.action;

import com.mdvcraft.mdvsocial.bedrock.forms.BlockedPlayersForms;
import com.mdvcraft.mdvsocial.bedrock.forms.FriendsForms;
import com.mdvcraft.mdvsocial.bedrock.forms.LinkMessages;
import com.mdvcraft.mdvsocial.bedrock.forms.MMOCoreForms;
import com.mdvcraft.mdvsocial.bedrock.forms.MailForms;
import com.mdvcraft.mdvsocial.bedrock.forms.PartyForms;
import com.mdvcraft.mdvsocial.bedrock.forms.TitlesForms;
import com.mdvcraft.mdvsocial.bedrock.forms.TpaForms;
import com.mdvcraft.mdvsocial.bedrock.menu.BedrockMenuParser;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuButton;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuContext;
import com.mdvcraft.mdvsocial.bedrock.port.BedrockHost;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.bedrock.port.TitlesPort;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockActionRegistryTest {

    private BedrockHost host;
    private TitlesForms titles;
    private MailForms mail;
    private BedrockActionRegistry registry;

    @BeforeEach
    void setUp() {
        host = mock(BedrockHost.class);
        when(host.normalizeAction(anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class).trim().toUpperCase(Locale.ROOT));
        titles = mock(TitlesForms.class);
        mail = mock(MailForms.class);
        registry = new BedrockActionRegistry(host);
        BuiltinBedrockActions.registerAll(registry, new BuiltinBedrockActions.Dependencies(
                host, new BedrockNavigation(host), mock(TitlesPort.class), mock(SocialPort.class), titles, mail,
                mock(FriendsForms.class), mock(PartyForms.class), mock(MMOCoreForms.class),
                mock(BlockedPlayersForms.class), mock(TpaForms.class), mock(LinkMessages.class)));
    }

    private static BedrockMenuButton button(String action, String permission) {
        return new BedrockMenuButton("b", "B", action, "", List.of(), true, "always", "", "true",
                "", "", "", "", permission, true, "", "");
    }

    private static BedrockMenuContext context(String menuId) {
        return new BedrockMenuContext(menuId, 1, "", 1, null, "", false);
    }

    @Test
    void bedrockAliasesAreNormalized() {
        assertEquals("OPEN_BEDROCK_FRIENDS", registry.normalize("bedrock_friends"));
        assertEquals("OPEN_MMOCORE_CLASSES", registry.normalize("mmocore_classes"));
        assertEquals("NONE", registry.normalize("info"));
        assertEquals("OPEN_MENU", registry.normalize("open_menu"));
    }

    @Test
    void dispatchRunsRegisteredHandler() {
        Player player = mock(Player.class);
        registry.dispatch(player, button("open_titles", ""), context("main"));
        verify(titles).openHome(player);
    }

    @Test
    void dispatchUsesMenuAsMailReturnTarget() {
        Player player = mock(Player.class);
        registry.dispatch(player, button("START_MAIL_SEND", ""), context(""));
        verify(mail).openCompose(player, "correo", 1);
    }

    @Test
    void dispatchStopsWithoutPermission() {
        Player player = mock(Player.class);
        when(player.hasPermission("vip")).thenReturn(false);
        registry.dispatch(player, button("OPEN_TITLES", "vip"), context("main"));
        verify(titles, never()).openHome(player);
        verify(host).message(player, "no-permission");
    }

    @Test
    void customActionsCanBeRegistered() {
        Player player = mock(Player.class);
        List<String> calls = new ArrayList<>();
        registry.register((p, b, c) -> calls.add(c.menuId()), "MY_ACTION");
        registry.dispatch(player, button("my_action", ""), context("tienda"));
        assertEquals(List.of("tienda"), calls);
    }

    @Test
    void everyActionInBundledMenusIsRegistered() throws Exception {
        Path folder = Path.of(getClass().getClassLoader().getResource("MenusBedrock").toURI());
        BedrockMenuParser parser = new BedrockMenuParser(registry::normalize);
        List<String> missing = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yml")).toList()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                if ("protections".equalsIgnoreCase(yaml.getString("managed-by", "")))
                    continue;
                String id = file.getFileName().toString().replace(".yml", "");
                for (List<BedrockMenuButton> page : parser.parse(id, yaml).pages().values()) {
                    for (BedrockMenuButton b : page) {
                        checked++;
                        if (!registry.isRegistered(b.action()))
                            missing.add(id + "." + b.id() + " -> " + b.action());
                    }
                }
            }
        }
        assertTrue(checked > 0, "bundled menus were found");
        assertTrue(missing.isEmpty(), "Unregistered actions: " + missing);
    }
}

package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.mdvcraft.mdvsocial.ProtectionMenuFiles.*;

class ProtectionMenuFilesTest {
    @TempDir Path folder;

    private YamlConfiguration resource(String path) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            config.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return config;
    }

    @Test void bundledMenusHaveNonOverlappingLayoutsAndNativeLabels() throws Exception {
        for (String id : MENUS) {
            YamlConfiguration java = resource("Menus/" + id + ".yml");
            assertDoesNotThrow(() -> validate(id, java));
            assertEquals("BLACK_STAINED_GLASS_PANE", java.getString("filler.material"));
            assertFalse(java.contains("navigation.close"));
            YamlConfiguration bedrock = resource("MenusBedrock/" + id + ".yml");
            assertTrue(bedrock.isString("navigation.back"));
            assertNotNull(bedrock.getString("title"));
        }
        assertEquals(27, resource("Menus/" + CONFIRM + ".yml").getInt("size"));
        assertEquals(List.of(20, 22, 24), resource("Menus/" + OPTIONS + ".yml").getIntegerList("content-slots"));
        resource("MenusBedrock/" + INPUT + ".yml");
    }

    @Test void mainUsesFourSlotsAndExpandsTwoRowsBelowWithoutPages() throws Exception {
        var config = resource("Menus/" + MAIN + ".yml");
        assertEquals(List.of(19, 21, 23, 25), slots(MAIN, config, 4));
        assertEquals(List.of(19, 21, 23, 25, 37, 39, 41, 43), slots(MAIN, config, 5));
        var page = page(8, 8, 12, paginated(MAIN));
        assertEquals(0, page.index());
        assertEquals(8, page.to());
        assertFalse(page.previous());
        assertFalse(page.next());
        assertThrows(IllegalArgumentException.class, () -> page(9, 8, 0, false));
    }

    @Test void listNavigationOnlyAppearsWhenAnotherPageExists() {
        for (String id : List.of(MEMBERS, SEARCH)) {
            assertTrue(paginated(id));
            for (int count : List.of(0, 1, 27, 28)) {
                assertFalse(page(count, 28, 0, true).previous());
                assertFalse(page(count, 28, 0, true).next());
            }
            Page first = page(29, 28, 0, true);
            assertFalse(first.previous());
            assertTrue(first.next());
            Page last = page(29, 28, 1, true);
            assertTrue(last.previous());
            assertFalse(last.next());
            assertEquals(28, last.from());
            assertEquals(29, last.to());
        }
    }

    @Test void editsSurviveReloadAndBadLayoutsFallBackWithoutOverwriting() throws Exception {
        MDVSocialPlugin plugin = mock(MDVSocialPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        doAnswer(call -> {
            String name = call.getArgument(0);
            Path file = folder.resolve(name);
            Files.createDirectories(file.getParent());
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) { Files.copy(in, file); }
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));
        var files = new ProtectionMenuFiles(plugin);
        files.reload();
        Path path = folder.resolve("Menus/protes.yml");
        YamlConfiguration edited = YamlConfiguration.loadConfiguration(path.toFile());
        edited.set("title", "Mi título editado");
        edited.save(path.toFile());
        files.reload();
        assertEquals("Mi título editado", files.get(false, MAIN).getString("title"));
        edited.set("content-slots", List.of(49)); // Would replace the back button.
        edited.save(path.toFile());
        files.reload();
        assertEquals(List.of(19, 21, 23, 25), files.get(false, MAIN).getIntegerList("content-slots"));
        assertEquals(List.of(49), YamlConfiguration.loadConfiguration(path.toFile()).getIntegerList("content-slots"));
        verify(plugin, times(11)).saveResource(anyString(), eq(false));
    }

    @Test void itemNamesCannotExpandOtherPlaceholders() {
        assertEquals("{world} $1 \\ test", replace("{item_name}", Map.of("item_name", "{world} $1 \\ test", "world", "hidden")));
    }

    @Test void onlineHeadsCopyTheActualProfileAndOfflineMembersKeepTheirIdentity() {
        UUID uuid = UUID.randomUUID();
        SkullMeta meta = mock(SkullMeta.class);
        Player online = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(online.getPlayerProfile()).thenReturn(profile);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(online);
            PlayerProtectionsMenuManager.applyHeadProfile(meta, uuid);
            verify(meta).setPlayerProfile(profile);
            verify(meta, never()).setOwningPlayer(any());
            reset(meta);
            OfflinePlayer offline = mock(OfflinePlayer.class);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
            bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);
            PlayerProtectionsMenuManager.applyHeadProfile(meta, uuid);
            verify(meta).setOwningPlayer(offline);
            verify(meta, never()).setPlayerProfile(any());
        }
    }
}

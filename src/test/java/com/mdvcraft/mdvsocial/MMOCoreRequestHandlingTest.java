package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MMOCoreRequestHandlingTest {

    private final UUID requestId = new UUID(9, 9);
    private final UUID steve = new UUID(0, 1);
    private final UUID alex = new UUID(0, 2);

    private static YamlConfiguration bundledConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                MMOCoreRequestHandlingTest.class.getClassLoader().getResourceAsStream("config.yml"),
                StandardCharsets.UTF_8));
    }

    private static List<Component> flatten(Component component) {
        List<Component> out = new ArrayList<>();
        out.add(component);
        component.children().forEach(child -> out.addAll(flatten(child)));
        return out;
    }

    private MMOCoreRequest request(Object handle) {
        return new MMOCoreRequest(handle, MMOCoreRequestWatcher.FRIEND_REQUEST, requestId, steve, "Steve", alex);
    }

    @Test
    void javaButtonsUseTheRequestIdNotTheSender() {
        MDVSocialPlugin plugin = mock(MDVSocialPlugin.class);
        when(plugin.getConfig()).thenReturn(bundledConfig());
        Player target = mock(Player.class);
        new MMOCoreRequestNotifier(plugin, mock(QuickAcceptService.class), new MMOCoreRequestNotifier.Spec(
                "party-invites", MMOCoreRequestNotifier.PARTY_INVITE_TYPE, (p, r) -> false, (p, r) -> { },
                (p, r) -> { })).notify(request(new Object()), target);

        ArgumentCaptor<Component> lines = ArgumentCaptor.forClass(Component.class);
        verify(target, atLeastOnce()).sendMessage(lines.capture());
        List<String> commands = lines.getAllValues().stream()
                .flatMap(line -> flatten(line).stream())
                .map(Component::clickEvent)
                .filter(e -> e != null && e.action() == ClickEvent.Action.RUN_COMMAND)
                .map(ClickEvent::value).toList();
        assertTrue(commands.contains("/mmocore:party accept " + requestId), commands.toString());
        assertTrue(commands.stream().noneMatch(command -> command.contains(steve.toString())));
    }

    @Test
    void repeatedFriendRequestIsDroppedSilently() {
        MDVSocialPlugin plugin = mock(MDVSocialPlugin.class);
        MMOCoreRequestNotifier notices = mock(MMOCoreRequestNotifier.class);
        Player target = mock(Player.class);
        Object handle = new Object();
        when(plugin.findPendingMMOCoreRequest(target, MMOCoreRequestWatcher.FRIEND_REQUEST, steve, handle))
                .thenReturn(new Object());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            new FriendRequestRules(plugin, notices).onNewRequest(request(handle), target);
        }

        verify(plugin).dropMMOCoreRequest(requestId);
        verify(notices, never()).notify(any(), any());
    }

    @Test
    void mutualFriendRequestAcceptsTheOlderOne() {
        MDVSocialPlugin plugin = mock(MDVSocialPlugin.class);
        MMOCoreRequestNotifier notices = mock(MMOCoreRequestNotifier.class);
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(alex);
        Player creator = mock(Player.class);
        Object reverse = new Object();
        when(plugin.findPendingMMOCoreRequest(creator, MMOCoreRequestWatcher.FRIEND_REQUEST, alex, null))
                .thenReturn(reverse);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(steve)).thenReturn(creator);
            new FriendRequestRules(plugin, notices).onNewRequest(request(new Object()), target);
        }

        verify(plugin).dropMMOCoreRequest(requestId);
        verify(plugin).resolveMMOCoreRequest(creator, reverse, true);
        verify(notices, never()).notify(any(), any());
    }

    @Test
    void requestToSomeoneYouBlockedIsWithdrawn() {
        MDVSocialPlugin plugin = mock(MDVSocialPlugin.class);
        MMOCoreRequestNotifier notices = mock(MMOCoreRequestNotifier.class);
        PlayerBlockService blocks = mock(PlayerBlockService.class);
        when(plugin.getBlockService()).thenReturn(blocks);
        when(blocks.isBlocked(steve, alex)).thenReturn(true);
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(alex);
        when(target.getName()).thenReturn("Alex");
        Player creator = mock(Player.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(steve)).thenReturn(creator);
            new FriendRequestRules(plugin, notices).onNewRequest(request(new Object()), target);
        }

        verify(plugin).dropMMOCoreRequest(requestId);
        verify(plugin).msg(creator, "friend-request-target-blocked", java.util.Map.of("target", "Alex"));
        verify(notices, never()).notify(any(), any());
    }

    @Test
    void newFriendRequestIsAnnounced() {
        MDVSocialPlugin plugin = mock(MDVSocialPlugin.class);
        MMOCoreRequestNotifier notices = mock(MMOCoreRequestNotifier.class);
        Player target = mock(Player.class);
        MMOCoreRequest request = request(new Object());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(steve)).thenReturn(mock(Player.class));
            new FriendRequestRules(plugin, notices).onNewRequest(request, target);
        }

        verify(notices).notify(request, target);
        verify(plugin, never()).dropMMOCoreRequest(any());
    }
}

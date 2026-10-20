package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TpaServiceTest {

    private final UUID senderId = new UUID(0, 1);
    private final UUID targetId = new UUID(0, 2);
    private TpaHost host;
    private TpaTeleporter teleporter;
    private TpaNotifier notifier;
    private Player sender;
    private Player target;

    @BeforeEach
    void setUp() {
        host = mock(TpaHost.class);
        when(host.canContact(any(), any())).thenReturn(true);
        teleporter = mock(TpaTeleporter.class);
        notifier = mock(TpaNotifier.class);
        World world = mock(World.class);
        sender = player(senderId, "Steve", world);
        target = player(targetId, "Alex", world);
        when(sender.canSee(target)).thenReturn(true);
    }

    private static Player player(UUID id, String name, World world) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    private TpaService service(TpaSettings settings) {
        return new TpaService(host, new TpaMessages(() -> null, raw -> raw), () -> settings, teleporter, notifier);
    }

    private static TpaSettings ignoringParty(boolean ignore) {
        TpaSettings d = TpaSettings.DEFAULTS;
        return new TpaSettings(true, d.requestTimeoutSeconds(), d.cooldownSeconds(), d.teleportDelaySeconds(),
                d.movementGraceSeconds(), true, true, true, d.invulnerabilitySeconds(), true, true, ignore);
    }

    @Test
    void partyMembersAreAcceptedRightAway() {
        when(host.sameParty(sender, target)).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(senderId)).thenReturn(sender);
            assertTrue(service(TpaSettings.DEFAULTS).request(sender, target));
        }
        verify(teleporter).start(sender, target);
        verify(notifier, never()).notify(any(), any(), any(), any());
        verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("es de tu grupo"));
        verify(target).sendMessage(org.mockito.ArgumentMatchers.contains("es de tu grupo"));
        verify(host).closeMenus(sender);
    }

    @Test
    void autoAcceptCanBeSwitchedOff() {
        when(host.sameParty(sender, target)).thenReturn(true);
        assertTrue(service(ignoringParty(true)).request(sender, target));
        verify(teleporter, never()).start(any(), any());
        verify(notifier).notify(any(), any(), any(), any());
    }

    @Test
    void refusedRequestKeepsTheMenusOpen() {
        TpaService service = service(TpaSettings.DEFAULTS);
        assertTrue(service.request(sender, target));
        // Second one within the cooldown.
        assertFalse(service.request(sender, target));
        verify(host, org.mockito.Mockito.times(1)).closeMenus(sender);
        assertFalse(service.request(sender, null));
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(anyString());
    }
}

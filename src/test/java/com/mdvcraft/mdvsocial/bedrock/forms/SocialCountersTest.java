package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.MailPort;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.bedrock.port.TeleportPort;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialCountersTest {

    private final UUID uuid = new UUID(0, 7);
    private Player player;
    private SocialPort social;
    private MailPort mail;
    private TeleportPort teleport;
    private YamlConfiguration config;
    private SocialCounters counters;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        social = mock(SocialPort.class);
        mail = mock(MailPort.class);
        teleport = mock(TeleportPort.class);
        counters = new SocialCounters(plugin, social, mail, teleport);
    }

    @Test
    void badgeIsHiddenWhenZeroButPlainNumberIsNot() {
        when(social.pendingRequests(player, SocialPort.FRIEND_REQUEST)).thenReturn(List.of());
        assertEquals("Amigos y Solicitudes", counters.apply("Amigos y Solicitudes{friend_requests_badge}", player));
        assertEquals("Solicitudes (0)", counters.apply("Solicitudes ({friend_requests})", player));
    }

    @Test
    void badgeShowsCountWithConfiguredFormat() {
        when(social.pendingRequests(player, SocialPort.PARTY_INVITE)).thenReturn(List.of(new Object(), new Object()));
        when(mail.unreadCount(uuid)).thenReturn(3);
        assertEquals("Grupo (2)", counters.apply("Grupo{party_invites_badge}", player));

        config.set("bedrock.badges.format", " &e[{count}]");
        assertEquals("Cartas &e[3]", counters.apply("Cartas{unread_mail_badge}", player));
    }

    @Test
    void countsAreOnlyComputedWhenUsed() {
        assertEquals("Sin marcas {otra}", counters.apply("Sin marcas {otra}", player));
        verify(social, never()).pendingRequests(any(), any());
        verify(mail, never()).unreadCount(any());
        verify(teleport, never()).pending(any());
    }

    @Test
    void socialNotificationsAddEverythingUpAndHideZero() {
        when(social.pendingRequests(player, SocialPort.FRIEND_REQUEST)).thenReturn(List.of(new Object()));
        when(social.pendingRequests(player, SocialPort.PARTY_INVITE)).thenReturn(List.of());
        when(mail.unreadCount(uuid)).thenReturn(2);
        when(teleport.pending(player)).thenReturn(List.of(new TeleportPort.RequestView(uuid, "Steve", 30)));
        assertEquals("Social y Amigos (4)", counters.apply("Social y Amigos{social_notifications_badge}", player));

        when(social.pendingRequests(player, SocialPort.FRIEND_REQUEST)).thenReturn(List.of());
        when(mail.unreadCount(uuid)).thenReturn(0);
        when(teleport.pending(player)).thenReturn(List.of());
        assertEquals("Social y Amigos", counters.apply("Social y Amigos{social_notifications_badge}", player));
    }

    @Test
    void tpaRequestsAreCounted() {
        when(teleport.pending(player)).thenReturn(List.of(new TeleportPort.RequestView(uuid, "Steve", 30)));
        assertEquals("Viajes (1)", counters.apply("Viajes{tpa_requests_badge}", player));
        assertEquals("Solicitudes (1)", counters.apply("Solicitudes ({tpa_requests})", player));
    }
}

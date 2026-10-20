package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.MailPort;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.bedrock.port.TeleportPort;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.IntSupplier;

/**
 * Pending-item counters usable in any MenusBedrock text:
 *
 * <ul>
 * <li>{@code {friend_requests}}, {@code {party_invites}}, {@code {unread_mail}},
 * {@code {tpa_requests}}:
 * the plain number, 0 included (for inner menus).</li>
 * <li>{@code {friend_requests_badge}}, {@code {party_invites_badge}},
 * {@code {unread_mail_badge}}, {@code {tpa_requests_badge}}: " (n)" formatted with
 * {@code bedrock.badges.format}, or nothing when n is 0 (for the main menu).</li>
 * <li>{@code {social_notifications}} and {@code {social_notifications_badge}}: all of
 * the above added up (new letters, friend requests, party invites, TPA requests).</li>
 * </ul>
 *
 * Each count is computed only if the text uses one of its tokens.
 */
public final class SocialCounters {

    private final JavaPlugin plugin;
    private final SocialPort social;
    private final MailPort mail;
    private final TeleportPort teleport;

    public SocialCounters(JavaPlugin plugin, SocialPort social, MailPort mail, TeleportPort teleport) {
        this.plugin = plugin;
        this.social = social;
        this.mail = mail;
        this.teleport = teleport;
    }

    public String apply(String raw, Player player) {
        if (raw == null || player == null || raw.indexOf('{') < 0)
            return raw;
        String text = raw;
        text = replace(text, "friend_requests",
                () -> social.pendingRequests(player, SocialPort.FRIEND_REQUEST).size());
        text = replace(text, "party_invites",
                () -> social.pendingRequests(player, SocialPort.PARTY_INVITE).size());
        text = replace(text, "unread_mail", () -> mail.unreadCount(player.getUniqueId()));
        text = replace(text, "tpa_requests", () -> teleport.pending(player).size());
        // Everything above together, for the "Social y Amigos" button of the main menu.
        text = replace(text, "social_notifications",
                () -> social.pendingRequests(player, SocialPort.FRIEND_REQUEST).size()
                + social.pendingRequests(player, SocialPort.PARTY_INVITE).size()
                + mail.unreadCount(player.getUniqueId())
                + teleport.pending(player).size());
        return text;
    }

    private String replace(String text, String name, IntSupplier count) {
        String plain = "{" + name + "}";
        String badge = "{" + name + "_badge}";
        if (!text.contains(plain) && !text.contains(badge))
            return text;
        int n = Math.max(0, count.getAsInt());
        return text.replace(plain, String.valueOf(n)).replace(badge, badge(n));
    }

    String badge(int count) {
        if (count <= 0)
            return "";
        String format = plugin.getConfig().getString("bedrock.badges.format", " ({count})");
        return (format == null ? " ({count})" : format).replace("{count}", String.valueOf(count));
    }
}

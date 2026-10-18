package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.bedrock.port.MailPort;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Mailbox data of MDVSocialPlugin for the Bedrock mail forms. */
final class BedrockMailAdapter implements MailPort {

    private final MDVSocialPlugin plugin;

    BedrockMailAdapter(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enabled() {
        return plugin.mailEnabled();
    }

    @Override
    public List<String> ids(UUID owner) {
        return plugin.getMailIds(owner);
    }

    @Override
    public int mailboxLimit(Player player) {
        return plugin.getMailboxLimit(player);
    }

    @Override
    public MailView find(UUID owner, String id) {
        MailSqliteStore.TrackedMail mail = plugin.mailData();
        String base = plugin.mailPath(owner, "letters." + id);
        if (mail == null || !mail.contains(base))
            return null;
        return new MailView(
                id,
                mail.getString(base + ".from-name", "Desconocido"),
                mail.getString(base + ".from-uuid", ""),
                plugin.mailMessageForDisplay(base),
                mail.getString(base + ".type", ""),
                mail.getBoolean(base + ".read", false),
                mail.getLong(base + ".sent-at", 0L),
                mail.getLong(base + ".expires-at", 0L));
    }

    @Override
    public int unreadCount(UUID owner) {
        MailSqliteStore.TrackedMail mail = plugin.mailData();
        if (mail == null || !plugin.mailEnabled())
            return 0;
        int unread = 0;
        for (String id : plugin.getMailIds(owner))
            if (!mail.getBoolean(plugin.mailPath(owner, "letters." + id) + ".read", false))
                unread++;
        return unread;
    }

    @Override
    public void markRead(UUID owner, String id) {
        plugin.mailData().set(plugin.mailPath(owner, "letters." + id) + ".read", true);
        plugin.saveMailData();
    }

    @Override
    public void delete(Player player, String id) {
        plugin.deleteMail(player, id);
    }

    @Override
    public void handleClanInvite(Player player, String id, boolean accept, int page) {
        plugin.handleClanInviteMailAction(player, id, accept, page);
    }

    @Override
    public void blockSender(Player player, String senderUuid) {
        plugin.blockMailSender(player, senderUuid);
    }

    @Override
    public void sendByName(Player sender, String targetName, String message) {
        plugin.sendMailByName(sender, targetName, message);
    }

    @Override
    public void sendByUuid(Player sender, UUID targetUuid, String fallbackName, String message) {
        plugin.sendMailByUuid(sender, targetUuid, fallbackName, message);
    }

    @Override
    public boolean checkCooldown(Player player) {
        return plugin.checkMailCooldown(player);
    }

    @Override
    public int maxLength() {
        return plugin.getMaxMailLength();
    }

    @Override
    public String formatTime(long millis) {
        return plugin.formatTime(millis);
    }

    @Override
    public String daysLeft(long expiresAt) {
        return plugin.daysLeftText(expiresAt);
    }

    @Override
    public String shorten(String text, int max) {
        return plugin.shorten(text, max);
    }
}

package com.mdvcraft.mdvsocial.bedrock.port;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Mailbox operations used by the Bedrock mail forms. */
public interface MailPort {

    record MailView(String id, String fromName, String fromUuid, String message, String type,
            boolean read, long sentAt, long expiresAt) {
    }

    boolean enabled();

    List<String> ids(UUID owner);

    int mailboxLimit(Player player);

    /** @return the letter, or null when it does not exist. */
    MailView find(UUID owner, String id);

    void markRead(UUID owner, String id);

    /** Letters the owner has not opened yet. */
    int unreadCount(UUID owner);

    void delete(Player player, String id);

    void handleClanInvite(Player player, String id, boolean accept, int page);

    /** Blocks the sender of a letter through the general block list. */
    void blockSender(Player player, String senderUuid);

    /** @return true when the player may send a letter now; otherwise tells them how long to wait. */
    boolean checkCooldown(Player player);

    void sendByName(Player sender, String targetName, String message);

    void sendByUuid(Player sender, UUID targetUuid, String fallbackName, String message);

    int maxLength();

    String formatTime(long millis);

    String daysLeft(long expiresAt);

    String shorten(String text, int max);
}

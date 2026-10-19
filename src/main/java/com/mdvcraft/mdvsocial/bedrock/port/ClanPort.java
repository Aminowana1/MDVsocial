package com.mdvcraft.mdvsocial.bedrock.port;

import org.bukkit.entity.Player;

import java.util.UUID;

/** MDVClans operations used by the Bedrock clan invite picker (MDVClans is optional). */
public interface ClanPort {

    /** Whether {@code player} belongs to any clan (they can't be invited then). */
    boolean isInClan(UUID player);

    /** Invites through MDVClans, which checks permissions and limits and tells the inviter. */
    void invite(Player inviter, String targetName);
}

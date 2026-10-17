package com.mdvcraft.mdvsocial.bedrock.port;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Friends and party (MMOCore) operations used by the Bedrock social forms. */
public interface SocialPort {

    String FRIEND_REQUEST = "FriendRequest";
    String PARTY_INVITE = "PartyInvite";

    record PartyView(boolean exists, int memberCount, int maxMembers, List<String> members) {
    }

    boolean isFriend(Player player, UUID targetUuid);

    List<UUID> friends(Player player);

    /** Pending MMOCore requests of the given simple class name. */
    List<Object> pendingRequests(Player player, String kind);

    String requestCreatorName(Object request);

    boolean resolveRequest(Player player, Object request, boolean accept);

    void inviteFriend(Player player, UUID targetUuid, String fallbackName);

    boolean removeFriend(Player player, UUID targetUuid, String fallbackName);

    void inviteFriendToParty(Player player, UUID targetUuid, String fallbackName);

    PartyView party(Player player);

    /** @return true when a new party was created. */
    boolean createParty(Player player);

    void leaveParty(Player player);

    void invitePlayerToParty(Player player, Player target);

    void syncPartyPermission(Player player);
}

package com.mdvcraft.mdvsocial.bedrock.port;

import com.mdvcraft.mdvsocial.players.PlayerRanking;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

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

    /** UUIDs of the player's party members, the player included; empty without a party. */
    Set<UUID> partyMembers(Player player);

    /**
     * Online players to offer in a "choose a player" list: friends, party, clan,
     * then the rest, without blocked players or the ones {@code exclude} rejects.
     */
    List<PlayerRanking.Ranked> rankPlayers(Player viewer, Predicate<UUID> exclude);
}

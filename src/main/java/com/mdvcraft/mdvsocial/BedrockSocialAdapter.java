package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** MMOCore friends and party bridge of MDVSocialPlugin for the Bedrock social forms. */
final class BedrockSocialAdapter implements SocialPort {

    private final MDVSocialPlugin plugin;

    BedrockSocialAdapter(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFriend(Player player, UUID targetUuid) {
        return plugin.isMMOCoreFriend(player, targetUuid);
    }

    @Override
    public List<UUID> friends(Player player) {
        return plugin.getMMOCoreFriendUuids(player);
    }

    @Override
    public List<Object> pendingRequests(Player player, String kind) {
        return plugin.getMMOCorePendingRequests(player, kind);
    }

    @Override
    public String requestCreatorName(Object request) {
        return plugin.getMMOCoreRequestCreatorName(request);
    }

    @Override
    public boolean resolveRequest(Player player, Object request, boolean accept) {
        return plugin.resolveMMOCoreRequest(player, request, accept);
    }

    @Override
    public void inviteFriend(Player player, UUID targetUuid, String fallbackName) {
        plugin.inviteMMOCoreFriend(player, targetUuid, fallbackName);
    }

    @Override
    public boolean removeFriend(Player player, UUID targetUuid, String fallbackName) {
        return plugin.removeMMOCoreFriend(player, targetUuid, fallbackName);
    }

    @Override
    public void inviteFriendToParty(Player player, UUID targetUuid, String fallbackName) {
        plugin.inviteFriendToParty(player, targetUuid, fallbackName);
    }

    @Override
    public PartyView party(Player player) {
        Object party = plugin.getMMOCoreParty(player);
        return new PartyView(party != null,
                plugin.countMMOCorePartyMembers(party),
                plugin.getConfiguredPartyMaxMembers(),
                plugin.getMMOCorePartyMemberNames(party));
    }

    @Override
    public boolean createParty(Player player) {
        try {
            Object created = plugin.createMMOCoreParty(plugin.getMMOCorePlayerData(player));
            if (created == null)
                return false;
            plugin.syncScoreboardPartyPermission(player);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @Override
    public void leaveParty(Player player) {
        plugin.leaveMMOCoreParty(player);
    }

    @Override
    public void invitePlayerToParty(Player player, Player target) {
        plugin.invitePlayerToPartyFromBedrockForm(player, target);
    }

    @Override
    public void syncPartyPermission(Player player) {
        plugin.syncScoreboardPartyPermission(player);
    }
}

package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.bedrock.port.ClanPort;
import org.bukkit.entity.Player;

import java.util.UUID;

/** MDVClans bridge for the Bedrock clan forms. */
final class BedrockClanAdapter implements ClanPort {

    @Override
    public boolean isInClan(UUID player) {
        return MDVClansBridge.isInClan(player);
    }

    @Override
    public void invite(Player inviter, String targetName) {
        MDVClansBridge.invite(inviter, targetName);
    }
}

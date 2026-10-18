package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.bedrock.port.BlocksPort;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** General block list of MDVSocialPlugin for the Bedrock "Jugadores Bloqueados" forms. */
final class BedrockBlocksAdapter implements BlocksPort {

    private final MDVSocialPlugin plugin;

    BedrockBlocksAdapter(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<UUID> blocked(Player player) {
        return plugin.getBlockService().blockedPlayers(player.getUniqueId());
    }

    @Override
    public void blockByName(Player player, String targetName) {
        plugin.blockPlayerByName(player, targetName);
    }

    @Override
    public void unblockByName(Player player, String targetName) {
        plugin.unblockPlayerByName(player, targetName);
    }

    @Override
    public void unblock(Player player, UUID target) {
        plugin.unblockPlayer(player, target, Bukkit.getOfflinePlayer(target).getName());
    }
}

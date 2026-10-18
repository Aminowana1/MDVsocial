package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.tpa.TpaHost;
import com.mdvcraft.mdvsocial.tpa.TpaRequest;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;

/** What the TPA module needs from MDVSocialPlugin: blocks, friends and Bedrock forms. */
final class TpaHostAdapter implements TpaHost {

    private final MDVSocialPlugin plugin;

    TpaHostAdapter(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public String color(String raw) {
        return plugin.color(raw);
    }

    @Override
    public boolean isBedrock(Player player) {
        return plugin.isBedrockPlayer(player);
    }

    @Override
    public boolean canContact(UUID from, UUID to) {
        PlayerBlockService blocks = plugin.getBlockService();
        return blocks == null || blocks.canContact(from, to);
    }

    @Override
    public Collection<UUID> friends(Player player) {
        return plugin.getMMOCoreFriendUuids(player);
    }

    @Override
    public void openBedrockRequest(Player target, TpaRequest request) {
        plugin.bedrock().tpa().openRequest(target, request.sender());
    }
}

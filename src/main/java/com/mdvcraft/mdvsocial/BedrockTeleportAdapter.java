package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.bedrock.port.TeleportPort;
import com.mdvcraft.mdvsocial.tpa.TpaRequest;
import com.mdvcraft.mdvsocial.tpa.TpaService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** TPA service of MDVSocialPlugin for the Bedrock "Viajes" forms. */
final class BedrockTeleportAdapter implements TeleportPort {

    private final MDVSocialPlugin plugin;

    BedrockTeleportAdapter(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    private TpaService service() {
        return plugin.tpa().service();
    }

    @Override
    public boolean checkAccess(Player player) {
        return service().checkAccess(player);
    }

    @Override
    public List<RequestView> pending(Player player) {
        long now = System.currentTimeMillis();
        List<RequestView> views = new ArrayList<>();
        for (TpaRequest request : service().pending(player))
            views.add(new RequestView(request.sender(), name(request.sender()), request.remainingSeconds(now)));
        return views;
    }

    @Override
    public String outgoingTarget(Player player) {
        TpaRequest request = service().outgoing(player);
        return request == null ? null : name(request.target());
    }

    @Override
    public boolean request(Player player, Player target) {
        return service().request(player, target);
    }

    @Override
    public boolean accept(Player player, UUID sender) {
        return service().accept(player, sender);
    }

    @Override
    public boolean deny(Player player, UUID sender) {
        return service().deny(player, sender);
    }

    @Override
    public void cancelOutgoing(Player player) {
        service().cancelOutgoing(player);
    }

    private static String name(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }
}

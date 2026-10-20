package com.mdvcraft.mdvsocial.tpa;

import com.mdvcraft.mdvsocial.JsonChatFormat;
import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptRequest;
import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptService;
import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells a player about a received request: clickable JSON lines for Java
 * ({@code tpa.notice.java.format}) and, for Bedrock, a chat notice plus a
 * quick accept offer ({@code quick-accept.types.tpa}).
 */
final class TpaNotifier {

    static final String QUICK_ACCEPT_TYPE = "tpa";

    private final TpaHost host;
    private final QuickAcceptService quickAccept;

    TpaNotifier(TpaHost host, QuickAcceptService quickAccept) {
        this.host = host;
        this.quickAccept = quickAccept;
    }

    /** @param service answers the request when the quick accept gesture is completed */
    void notify(Player target, Player sender, TpaRequest request, TpaService service) {
        ConfigurationSection notice = host.plugin().getConfig().getConfigurationSection("tpa.notice");
        if (notice == null)
            return;
        Map<String, String> tokens = new HashMap<>();
        tokens.put("player", sender.getName());
        tokens.put("uuid", sender.getUniqueId().toString());
        tokens.put("seconds", String.valueOf(request.remainingSeconds(System.currentTimeMillis())));
        if (host.isBedrock(target))
            notifyBedrock(target, request, service, notice, tokens);
        else if (notice.getBoolean("java.enabled", true))
            for (Component line : JsonChatFormat.render(notice.getStringList("java.format"), tokens))
                target.sendMessage(line);
    }

    private void notifyBedrock(Player target, TpaRequest request, TpaService service, ConfigurationSection notice,
            Map<String, String> tokens) {
        if (!notice.getBoolean("bedrock.enabled", true))
            return;
        boolean offered = quickAccept.offer(target, new QuickAcceptRequest(QUICK_ACCEPT_TYPE,
                player -> onQuickAccept(player, request, service,
                        notice.getBoolean("bedrock.skip-confirmation-for-friends", true)),
                player -> service.isPending(request)));

        QuickAcceptSettings settings = quickAccept.settings(QUICK_ACCEPT_TYPE);
        tokens.put("sneaks", String.valueOf(settings.sneaksRequired()));
        tokens.put("quick_seconds", String.valueOf(settings.windowSeconds()));
        List<String> lines = notice.getStringList(offered ? "bedrock.message" : "bedrock.message-without-quick-accept");
        for (String line : lines)
            target.sendMessage(host.color(TpaMessages.replaceTokens(line, tokens)));
    }

    private void onQuickAccept(Player player, TpaRequest request, TpaService service, boolean skipForFriends) {
        if (skipForFriends && host.friends(player).contains(request.sender()))
            service.accept(player, request.sender());
        else
            host.openBedrockRequest(player, request);
    }
}

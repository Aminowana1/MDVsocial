package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptRequest;
import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptService;
import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells an invited player about a party invitation, replacing MMOCore's own
 * message: clickable JSON lines for Java (party-invites.java.format) and, for
 * Bedrock, a chat notice plus a quick accept offer (sneak to answer).
 */
final class PartyInviteNotifier {

    static final String QUICK_ACCEPT_TYPE = "party-invite";

    private final MDVSocialPlugin plugin;
    private final QuickAcceptService quickAccept;

    PartyInviteNotifier(MDVSocialPlugin plugin, QuickAcceptService quickAccept) {
        this.plugin = plugin;
        this.quickAccept = quickAccept;
    }

    void notify(Player target, UUID inviterUuid, String inviterName, Object request) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("player", inviterName == null || inviterName.isBlank() ? "Alguien" : inviterName);
        tokens.put("uuid", inviterUuid == null ? "" : inviterUuid.toString());
        if (plugin.isBedrockPlayer(target))
            notifyBedrock(target, inviterUuid, request, tokens);
        else
            notifyJava(target, tokens);
    }

    private void notifyJava(Player target, Map<String, String> tokens) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("party-invites.java.enabled", true))
            return;
        for (Component line : JsonChatFormat.render(config.getStringList("party-invites.java.format"), tokens))
            target.sendMessage(line);
    }

    private void notifyBedrock(Player target, UUID inviterUuid, Object request, Map<String, String> tokens) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("party-invites.bedrock.enabled", true))
            return;
        boolean offered = quickAccept.offer(target, new QuickAcceptRequest(QUICK_ACCEPT_TYPE,
                player -> onQuickAccept(player, inviterUuid, request),
                player -> plugin.getMMOCorePendingRequests(player, "PartyInvite").contains(request)));

        QuickAcceptSettings settings = quickAccept.settings(QUICK_ACCEPT_TYPE);
        tokens.put("sneaks", String.valueOf(settings.sneaksRequired()));
        tokens.put("seconds", String.valueOf(settings.windowSeconds()));
        List<String> lines = config.getStringList(offered
                ? "party-invites.bedrock.message"
                : "party-invites.bedrock.message-without-quick-accept");
        for (String line : lines)
            target.sendMessage(plugin.color(replace(line, tokens)));
    }

    private void onQuickAccept(Player player, UUID inviterUuid, Object request) {
        boolean skipForFriends = plugin.getConfig()
                .getBoolean("party-invites.bedrock.skip-confirmation-for-friends", true);
        if (skipForFriends && inviterUuid != null && plugin.isMMOCoreFriend(player, inviterUuid))
            plugin.bedrock().party().acceptInvite(player, request);
        else
            plugin.bedrock().party().openInvite(player, request);
    }

    private static String replace(String raw, Map<String, String> tokens) {
        String text = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : tokens.entrySet())
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        return text;
    }
}

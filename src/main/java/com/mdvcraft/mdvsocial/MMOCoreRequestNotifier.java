package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptRequest;
import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptService;
import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * Tells a player about an MMOCore request, replacing MMOCore's own message:
 * clickable JSON lines for Java ({@code <path>.java.format}) and, for Bedrock,
 * a chat notice plus a quick accept offer (sneak to answer). One instance per
 * request kind; party invites and friend requests only differ in their {@link Spec}.
 */
final class MMOCoreRequestNotifier {

    static final String PARTY_INVITE_TYPE = "party-invite";
    static final String FRIEND_REQUEST_TYPE = "friend-request";

    /**
     * @param configPath       config section with {@code java} and {@code bedrock} notices
     * @param quickAcceptType  key under {@code quick-accept.types}
     * @param skipConfirmation whether quick accept answers directly instead of asking
     * @param accept           accepts the request (quick accept without confirmation)
     * @param confirm          opens the Bedrock confirmation form
     */
    record Spec(String configPath, String quickAcceptType, BiPredicate<Player, MMOCoreRequest> skipConfirmation,
            BiConsumer<Player, MMOCoreRequest> accept, BiConsumer<Player, MMOCoreRequest> confirm) {
    }

    private final MDVSocialPlugin plugin;
    private final QuickAcceptService quickAccept;
    private final Spec spec;

    MMOCoreRequestNotifier(MDVSocialPlugin plugin, QuickAcceptService quickAccept, Spec spec) {
        this.plugin = plugin;
        this.quickAccept = quickAccept;
        this.spec = spec;
    }

    void notify(MMOCoreRequest request, Player target) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(spec.configPath());
        if (section == null)
            return;
        Map<String, String> tokens = new HashMap<>();
        tokens.put("player", request.creatorName() == null || request.creatorName().isBlank()
                ? "Alguien" : request.creatorName());
        // MMOCore's accept/deny commands look requests up by their own id.
        tokens.put("uuid", request.id() == null ? "" : request.id().toString());
        if (plugin.isBedrockPlayer(target))
            notifyBedrock(target, request, section, tokens);
        else if (section.getBoolean("java.enabled", true))
            for (Component line : JsonChatFormat.render(section.getStringList("java.format"), tokens))
                target.sendMessage(line);
    }

    private void notifyBedrock(Player target, MMOCoreRequest request, ConfigurationSection section,
            Map<String, String> tokens) {
        if (!section.getBoolean("bedrock.enabled", true))
            return;
        boolean offered = quickAccept.offer(target, new QuickAcceptRequest(spec.quickAcceptType(),
                player -> onQuickAccept(player, request),
                player -> plugin.getMMOCorePendingRequests(player, request.kind()).contains(request.handle())));

        QuickAcceptSettings settings = quickAccept.settings(spec.quickAcceptType());
        tokens.put("sneaks", String.valueOf(settings.sneaksRequired()));
        // {quick_seconds} like the TPA notice; {seconds} kept for older configs.
        tokens.put("quick_seconds", String.valueOf(settings.windowSeconds()));
        tokens.put("seconds", String.valueOf(settings.windowSeconds()));
        List<String> lines = section.getStringList(offered ? "bedrock.message" : "bedrock.message-without-quick-accept");
        for (String line : lines)
            target.sendMessage(plugin.color(replace(line, tokens)));
    }

    private void onQuickAccept(Player player, MMOCoreRequest request) {
        if (spec.skipConfirmation().test(player, request))
            spec.accept().accept(player, request);
        else
            spec.confirm().accept(player, request);
    }

    private static String replace(String raw, Map<String, String> tokens) {
        String text = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : tokens.entrySet())
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        return text;
    }
}

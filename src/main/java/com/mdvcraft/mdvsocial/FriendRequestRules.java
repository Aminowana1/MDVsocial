package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * What happens with a new MMOCore friend request before its notice, for every
 * source (MDVSocial menus, MMOCore's /friends GUI, other plugins):
 *
 * <ul>
 * <li>A request to someone the sender blocked is withdrawn, and the sender is
 * told to unblock them first.</li>
 * <li>A repeated request (same sender and receiver) is removed silently; the
 * receiver already has the first one.</li>
 * <li>A mutual request (the receiver had already asked the sender) makes them
 * friends right away by accepting the older one.</li>
 * </ul>
 */
final class FriendRequestRules {

    private final MDVSocialPlugin plugin;
    private final MMOCoreRequestNotifier notices;

    FriendRequestRules(MDVSocialPlugin plugin, MMOCoreRequestNotifier notices) {
        this.plugin = plugin;
        this.notices = notices;
    }

    void onNewRequest(MMOCoreRequest request, Player target) {
        if (request.creator() == null) {
            notices.notify(request, target);
            return;
        }
        Player creator = Bukkit.getPlayer(request.creator());
        PlayerBlockService blocks = plugin.getBlockService();
        if (blocks != null && blocks.isBlocked(request.creator(), target.getUniqueId())) {
            plugin.dropMMOCoreRequest(request.id());
            if (creator != null)
                plugin.msg(creator, "friend-request-target-blocked", Map.of("target", target.getName()));
            return;
        }
        Object older = plugin.findPendingMMOCoreRequest(target, MMOCoreRequestWatcher.FRIEND_REQUEST,
                request.creator(), request.handle());
        if (older != null) {
            plugin.dropMMOCoreRequest(request.id());
            return;
        }
        Object reverse = creator == null ? null
                : plugin.findPendingMMOCoreRequest(creator, MMOCoreRequestWatcher.FRIEND_REQUEST,
                        target.getUniqueId(), null);
        if (reverse != null) {
            plugin.dropMMOCoreRequest(request.id());
            plugin.resolveMMOCoreRequest(creator, reverse, true);
            return;
        }
        notices.notify(request, target);
    }
}

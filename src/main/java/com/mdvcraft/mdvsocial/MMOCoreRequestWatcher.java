package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects new MMOCore requests (party invites and friend requests), whatever
 * created them (MDVSocial menus, MMOCore's own GUI or other plugins), and
 * hands each one to the handler of its kind once.
 *
 * Requests from a player the receiver blocked are removed silently, so the
 * sender learns nothing. MMOCore's own notice messages ("party-invite",
 * "friend-request") are expected to be silenced in its messages.yml.
 *
 * One pass reads MMOCore's global request map, so the cost depends on how many
 * requests are pending, not on how many players are online.
 */
final class MMOCoreRequestWatcher {

    static final String PARTY_INVITE = "PartyInvite";
    static final String FRIEND_REQUEST = "FriendRequest";

    /** Reacts to a new request whose target is online. */
    @FunctionalInterface
    interface Handler {
        void onNewRequest(MMOCoreRequest request, Player target);
    }

    private final MDVSocialPlugin plugin;
    private final Map<String, Handler> handlers = new LinkedHashMap<>();
    /** Requests already handled. Weak, so answered/expired ones are released. */
    private final Set<Object> seen = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private BukkitTask task;
    private boolean failureLogged;

    MMOCoreRequestWatcher(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    /** Wires the party invite and friend request notices. */
    static MMOCoreRequestWatcher create(MDVSocialPlugin plugin, QuickAcceptService quickAccept) {
        MMOCoreRequestWatcher watcher = new MMOCoreRequestWatcher(plugin);
        MMOCoreRequestNotifier partyNotices = new MMOCoreRequestNotifier(plugin, quickAccept,
                new MMOCoreRequestNotifier.Spec("party-invites", MMOCoreRequestNotifier.PARTY_INVITE_TYPE,
                        (player, request) -> plugin.getConfig()
                                .getBoolean("party-invites.bedrock.skip-confirmation-for-friends", true)
                                && request.creator() != null && plugin.isMMOCoreFriend(player, request.creator()),
                        (player, request) -> plugin.bedrock().party().acceptInvite(player, request.handle()),
                        (player, request) -> plugin.bedrock().party().openInvite(player, request.handle())));
        MMOCoreRequestNotifier friendNotices = new MMOCoreRequestNotifier(plugin, quickAccept,
                new MMOCoreRequestNotifier.Spec("friend-requests", MMOCoreRequestNotifier.FRIEND_REQUEST_TYPE,
                        (player, request) -> plugin.getConfig()
                                .getBoolean("friend-requests.bedrock.skip-confirmation", false),
                        (player, request) -> plugin.bedrock().friends().acceptRequest(player, request.handle()),
                        (player, request) -> plugin.bedrock().friends().openRequest(player, request.handle())));
        watcher.handlers.put(PARTY_INVITE, partyNotices::notify);
        watcher.handlers.put(FRIEND_REQUEST, new FriendRequestRules(plugin, friendNotices)::onNewRequest);
        return watcher;
    }

    void enable() {
        if (activeKinds().isEmpty())
            return;
        long interval = Math.max(5L, plugin.getConfig().getLong("mmocore-requests.check-interval-ticks",
                plugin.getConfig().getLong("party-invites.check-interval-ticks", 20L)));
        // First pass marks requests that already existed (e.g. after /reload) without re-notifying.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            selfCheck();
            scan(false);
        }, 1L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> scan(true), interval, interval);
    }

    void disable() {
        if (task != null)
            task.cancel();
        task = null;
        seen.clear();
    }

    /** Immediate pass, used right after MDVSocial itself sends a request. */
    void scanNow() {
        if (task != null)
            scan(true);
    }

    /** Kinds whose feature is switched on (party-invites.enabled, friend-requests.enabled). */
    private Set<String> activeKinds() {
        Set<String> kinds = new HashSet<>();
        if (plugin.getConfig().getBoolean("party-invites.enabled", true))
            kinds.add(PARTY_INVITE);
        if (plugin.getConfig().getBoolean("friend-requests.enabled", true))
            kinds.add(FRIEND_REQUEST);
        return kinds;
    }

    private void selfCheck() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return;
        try {
            plugin.getAllMMOCoreRequests();
            plugin.getLogger().info("Avisos de solicitudes de MMOCore activos (grupos y amistad).");
        } catch (Throwable ex) {
            failureLogged = true;
            plugin.getLogger().severe("No se pueden leer las solicitudes de MMOCore (" + ex.getMessage()
                    + "). Nadie recibirá avisos de invitaciones ni de amistad: vuelve a activar los mensajes"
                    + " party-invite y friend-request en messages.yml de MMOCore o revisa la versión de MMOCore.");
        }
    }

    private void scan(boolean notify) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return;
        Collection<?> requests;
        try {
            requests = plugin.getAllMMOCoreRequests();
        } catch (Throwable ex) {
            if (!failureLogged) {
                failureLogged = true;
                plugin.getLogger().warning("No se pudieron leer solicitudes de MMOCore: " + ex.getMessage());
            }
            return;
        }
        Set<String> kinds = activeKinds();
        for (Object handle : requests) {
            if (handle == null || !kinds.contains(handle.getClass().getSimpleName()) || !seen.add(handle))
                continue;
            if (!notify || timedOut(handle))
                continue;
            try {
                handle(describe(handle));
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo procesar una solicitud de MMOCore: " + ex.getMessage());
            }
        }
    }

    private void handle(MMOCoreRequest request) {
        Player target = request.target() == null ? null : Bukkit.getPlayer(request.target());
        if (target == null || !target.isOnline())
            return;
        // Same rule as letters: someone you blocked gets nothing through and learns nothing.
        if (request.creator() != null && plugin.getBlockService() != null
                && plugin.getBlockService().isBlocked(target.getUniqueId(), request.creator())) {
            plugin.dropMMOCoreRequest(request.id());
            return;
        }
        Handler handler = handlers.get(request.handle().getClass().getSimpleName());
        if (handler != null)
            handler.onNewRequest(request, target);
    }

    MMOCoreRequest describe(Object handle) throws Exception {
        UUID id = plugin.extractUuid(call(handle, "getUniqueId"));
        UUID creator = plugin.extractUuid(call(handle, "getCreator"));
        UUID target = plugin.extractUuid(call(handle, "getTarget"));
        return new MMOCoreRequest(handle, handle.getClass().getSimpleName(), id, creator,
                plugin.getMMOCoreRequestCreatorName(handle), target);
    }

    private boolean timedOut(Object request) {
        try {
            return Boolean.TRUE.equals(call(request, "isTimedOut"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object call(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        Method method = methods.get(type.getName() + "#" + name);
        if (method == null) {
            method = type.getMethod(name);
            methods.put(type.getName() + "#" + name, method);
        }
        return method.invoke(target);
    }
}

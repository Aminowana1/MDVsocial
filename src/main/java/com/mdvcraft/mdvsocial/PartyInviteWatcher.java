package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects new MMOCore party invitations, whatever created them (/grupo, menus,
 * MMOCore's own GUI or other plugins), and hands each one to
 * {@link PartyInviteNotifier} once. MMOCore's own invite chat message is
 * expected to be silenced in its messages.yml.
 *
 * One pass reads MMOCore's global request map, so the cost depends on how many
 * requests are pending, not on how many players are online.
 */
final class PartyInviteWatcher {

    private static final String PARTY_INVITE = "PartyInvite";

    private final MDVSocialPlugin plugin;
    private final PartyInviteNotifier notifier;
    /** Invitations already handled. Weak, so answered/expired ones are released. */
    private final Set<Object> seen = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private BukkitTask task;
    private boolean failureLogged;

    PartyInviteWatcher(MDVSocialPlugin plugin, PartyInviteNotifier notifier) {
        this.plugin = plugin;
        this.notifier = notifier;
    }

    void enable() {
        if (!plugin.getConfig().getBoolean("party-invites.enabled", true))
            return;
        long interval = Math.max(5L, plugin.getConfig().getLong("party-invites.check-interval-ticks", 20L));
        // First pass marks invitations that already existed (e.g. after /reload) without re-notifying.
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

    /** Immediate pass, used right after MDVSocial itself sends an invitation. */
    void scanNow() {
        if (task != null)
            scan(true);
    }

    private void selfCheck() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return;
        try {
            plugin.getAllMMOCoreRequests();
            plugin.getLogger().info("Avisos de invitaciones a grupo activos (mensaje de MMOCore reemplazado).");
        } catch (Throwable ex) {
            failureLogged = true;
            plugin.getLogger().severe("No se pueden leer las invitaciones de MMOCore (" + ex.getMessage()
                    + "). Nadie recibirá avisos de invitación: vuelve a activar el mensaje de invitación"
                    + " a party en messages.yml de MMOCore o revisa la versión de MMOCore.");
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
                plugin.getLogger().warning("No se pudieron leer invitaciones de MMOCore: " + ex.getMessage());
            }
            return;
        }
        for (Object request : requests) {
            if (request == null || !PARTY_INVITE.equalsIgnoreCase(request.getClass().getSimpleName())
                    || !seen.add(request))
                continue;
            if (!notify || timedOut(request))
                continue;
            try {
                handle(request);
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo avisar una invitación de grupo: " + ex.getMessage());
            }
        }
    }

    private void handle(Object request) throws Exception {
        UUID targetUuid = plugin.extractUuid(call(request, "getTarget"));
        Player target = targetUuid == null ? null : Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline())
            return;
        UUID inviterUuid = plugin.extractUuid(call(request, "getCreator"));
        // Same rule as letters: someone you blocked gets no notice and learns nothing.
        if (inviterUuid != null && plugin.getBlockService().isBlocked(target.getUniqueId(), inviterUuid))
            return;
        notifier.notify(target, inviterUuid, plugin.getMMOCoreRequestCreatorName(request), request);
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

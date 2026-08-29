package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the lifecycle of Bedrock Form responses.
 *
 * 1.6.5 intentionally does NOT debounce across forms. A SimpleForm response is
 * already a one-shot response; applying a global time debounce could consume the
 * first valid tap of the next form on touch devices.
 *
 * Each form receives a monotonically increasing token. Only the active token can
 * be consumed and each token can be consumed once. The selected action is then
 * executed on the Bukkit main thread after a very small, device-independent
 * delay (1 tick by default).
 */
final class BedrockUiSessionManager {

    private final MDVSocialPlugin plugin;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, Long> activeSession = new ConcurrentHashMap<>();
    private final Map<UUID, Long> consumedSession = new ConcurrentHashMap<>();

    BedrockUiSessionManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    long begin(Player player) {
        if (player == null)
            return 0L;

        long token = sequence.incrementAndGet();
        UUID uuid = player.getUniqueId();
        activeSession.put(uuid, token);
        consumedSession.remove(uuid);
        debug(player, "Form abierto. session=" + token);
        return token;
    }

    long current(Player player) {
        if (player == null)
            return 0L;
        return activeSession.getOrDefault(player.getUniqueId(), 0L);
    }

    void run(Player player, long expectedSession, Runnable action) {
        if (player == null || action == null || !player.isOnline())
            return;

        UUID uuid = player.getUniqueId();

        synchronized (this) {
            Long active = activeSession.get(uuid);
            if (expectedSession != 0L && (active == null || active.longValue() != expectedSession)) {
                debug(player, "Respuesta antigua descartada. expected=" + expectedSession + " active=" + active);
                return;
            }

            Long consumed = consumedSession.get(uuid);
            if (expectedSession != 0L && consumed != null && consumed.longValue() == expectedSession) {
                debug(player, "Respuesta duplicada descartada. session=" + expectedSession);
                return;
            }

            // Claim the response only after all validation has passed.
            if (expectedSession != 0L)
                consumedSession.put(uuid, expectedSession);
        }

        long delay = Math.max(0L, Math.min(5L,
                plugin.getConfig().getLong("bedrock.form-navigation.delay-ticks", 1L)));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline())
                return;

            Long active = activeSession.get(uuid);
            if (expectedSession != 0L && (active == null || active.longValue() != expectedSession)) {
                debug(player, "Accion cancelada porque otro Form sustituyo la sesion " + expectedSession + ".");
                return;
            }

            try {
                action.run();
            } catch (Throwable ex) {
                plugin.getLogger().severe("Error ejecutando accion Bedrock para " + player.getName()
                        + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }, delay);
    }

    void run(Player player, Runnable action) {
        run(player, current(player), action);
    }

    int dynamicPageSize(Player player) {
        int standard = Math.max(4, Math.min(15, plugin.getConfig().getInt("bedrock.dynamic-page-size", 6)));
        if (!plugin.getConfig().getBoolean("bedrock.mobile-safety.enabled", true) || !isTouch(player))
            return standard;
        return Math.max(4, Math.min(10,
                plugin.getConfig().getInt("bedrock.mobile-safety.dynamic-page-size", 5)));
    }

    boolean isTouch(Player player) {
        return inputKind(player) == InputKind.TOUCH;
    }

    void clear(Player player) {
        if (player == null)
            return;
        UUID uuid = player.getUniqueId();
        activeSession.remove(uuid);
        consumedSession.remove(uuid);
    }

    void clearAll() {
        activeSession.clear();
        consumedSession.clear();
    }

    void trace(Player player, String message) {
        debug(player, message);
    }

    private void debug(Player player, String message) {
        if (!plugin.getConfig().getBoolean("bedrock.form-navigation.debug",
                plugin.getConfig().getBoolean("bedrock.mobile-safety.debug", false)))
            return;
        plugin.getLogger().info("[BedrockUI] " + (player == null ? "?" : player.getName()) + ": " + message);
    }

    private InputKind inputKind(Player player) {
        if (player == null || !plugin.isBedrockPlayer(player))
            return InputKind.UNKNOWN;
        try {
            Object floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgatePlayer == null)
                return InputKind.UNKNOWN;

            for (String methodName : List.of("getInputMode", "getDeviceOs", "getDeviceOS")) {
                try {
                    Method method = floodgatePlayer.getClass().getMethod(methodName);
                    Object value = method.invoke(floodgatePlayer);
                    String name = value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
                    if (name.contains("TOUCH") || name.contains("ANDROID") || name.contains("IOS")
                            || name.contains("FIRE_OS") || name.contains("FIREOS"))
                        return InputKind.TOUCH;
                    if (name.contains("CONTROLLER") || name.contains("GAMEPAD"))
                        return InputKind.CONTROLLER;
                    if (name.contains("KEYBOARD") || name.contains("MOUSE"))
                        return InputKind.KEYBOARD_MOUSE;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return InputKind.UNKNOWN;
    }

    private enum InputKind {
        TOUCH,
        CONTROLLER,
        KEYBOARD_MOUSE,
        UNKNOWN
    }
}

package com.mdvcraft.mdvsocial.quickaccept;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Quick accept settings of one request type. Each value is read from
 * {@code quick-accept.types.<type>.<key>} and falls back to
 * {@code quick-accept.<key>}, so every feature can tune its own gesture.
 */
public record QuickAcceptSettings(boolean enabled, boolean bedrockOnly, int sneaksRequired, int windowSeconds,
        long maxGapMillis, String triggerSound, String expiredMessage) {

    public static QuickAcceptSettings from(ConfigurationSection quickAccept, String type) {
        ConfigurationSection specific = quickAccept == null ? null
                : quickAccept.getConfigurationSection("types." + type);
        boolean globalEnabled = quickAccept == null || quickAccept.getBoolean("enabled", true);
        return new QuickAcceptSettings(
                globalEnabled && bool(specific, quickAccept, "enabled", true),
                bool(specific, quickAccept, "bedrock-only", true),
                clamp(integer(specific, quickAccept, "sneaks-required", 2), 1, 10),
                clamp(integer(specific, quickAccept, "window-seconds", 30), 3, 600),
                clamp(integer(specific, quickAccept, "max-sneak-gap-millis", 700), 100, 5000),
                string(specific, quickAccept, "trigger-sound", "confirm"),
                string(specific, quickAccept, "messages.expired", ""));
    }

    private static boolean bool(ConfigurationSection specific, ConfigurationSection global, String key, boolean def) {
        if (specific != null && specific.contains(key))
            return specific.getBoolean(key);
        return global == null ? def : global.getBoolean(key, def);
    }

    private static int integer(ConfigurationSection specific, ConfigurationSection global, String key, int def) {
        if (specific != null && specific.contains(key))
            return specific.getInt(key);
        return global == null ? def : global.getInt(key, def);
    }

    private static String string(ConfigurationSection specific, ConfigurationSection global, String key, String def) {
        if (specific != null && specific.contains(key))
            return specific.getString(key, def);
        return global == null ? def : global.getString(key, def);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

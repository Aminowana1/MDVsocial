package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Values of the {@code tpa} config section, already clamped to sane ranges.
 *
 * @param requestTimeoutSeconds       how long a request can be answered
 * @param cooldownSeconds             wait between two requests of the same sender
 * @param teleportDelaySeconds        total wait after accepting (0 = instant)
 * @param movementGraceSeconds        first part of the wait in which moving is allowed
 * @param invulnerabilitySeconds      damage protection after arriving (0 = none)
 * @param ignorePartyAutoAccept       true: requests between party members are not accepted automatically
 */
public record TpaSettings(
        boolean enabled,
        int requestTimeoutSeconds,
        int cooldownSeconds,
        int teleportDelaySeconds,
        int movementGraceSeconds,
        boolean cancelDelayOnMove,
        boolean cancelDelayOnDamage,
        boolean cancelDelayOnAttack,
        int invulnerabilitySeconds,
        boolean invulnerabilityCancelOnAttack,
        boolean allowCrossWorld,
        boolean ignorePartyAutoAccept) {

    public static final TpaSettings DEFAULTS = new TpaSettings(true, 60, 15, 4, 1, true, true, true, 5, true, true,
            false);

    public static TpaSettings from(ConfigurationSection section) {
        if (section == null)
            return DEFAULTS;
        int delay = Math.max(0, section.getInt("teleport-delay-seconds", DEFAULTS.teleportDelaySeconds()));
        return new TpaSettings(
                section.getBoolean("enabled", DEFAULTS.enabled()),
                Math.max(5, section.getInt("request-timeout-seconds", DEFAULTS.requestTimeoutSeconds())),
                Math.max(0, section.getInt("cooldown-seconds", DEFAULTS.cooldownSeconds())),
                delay,
                Math.min(delay, Math.max(0, section.getInt("movement-grace-seconds", DEFAULTS.movementGraceSeconds()))),
                section.getBoolean("cancel-delay-on-move", DEFAULTS.cancelDelayOnMove()),
                section.getBoolean("cancel-delay-on-damage", DEFAULTS.cancelDelayOnDamage()),
                section.getBoolean("cancel-delay-on-attack", DEFAULTS.cancelDelayOnAttack()),
                Math.max(0, section.getInt("invulnerability-after-teleport-seconds",
                        DEFAULTS.invulnerabilitySeconds())),
                section.getBoolean("invulnerability-cancel-on-attack", DEFAULTS.invulnerabilityCancelOnAttack()),
                section.getBoolean("allow-cross-world", DEFAULTS.allowCrossWorld()),
                section.getBoolean("party-auto-accept.ignore", DEFAULTS.ignorePartyAutoAccept()));
    }

    /** Seconds of the wait in which the requester must stand still. */
    public int movementLockSeconds() {
        return Math.max(0, teleportDelaySeconds - movementGraceSeconds);
    }
}

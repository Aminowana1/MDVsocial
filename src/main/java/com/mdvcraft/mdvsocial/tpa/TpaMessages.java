package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Chat texts of {@code tpa.messages}. Tokens are written as {@code {player}};
 * the old MDVTools form {@code %player%} is accepted too. An empty message is
 * not sent.
 */
final class TpaMessages {

    static final String DEFAULT_PREFIX = "&6&l[&5&lMDVCRAFT&6&l]  &4»  &r";

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("disabled", "&cEl sistema de TPA está desactivado."),
            Map.entry("only-players", "&cEste comando solo puede usarlo un jugador."),
            Map.entry("no-permission", "&cNo tienes permiso para usar el TPA."),
            Map.entry("player-not-found", "&cJugador no encontrado o desconectado."),
            Map.entry("cannot-self", "&cNo puedes enviarte una solicitud a ti mismo."),
            Map.entry("cross-world-disabled", "&cNo puedes usar TPA entre mundos distintos."),
            Map.entry("cooldown", "&cDebes esperar &f{seconds}s &cantes de enviar otra solicitud."),
            Map.entry("request-sent",
                    "&eSolicitud de teletransporte enviada a &6{target}&e. &7Expira en &f{seconds} segundos&7."),
            Map.entry("request-not-delivered", "&cNo se pudo enviar la solicitud de teletransporte a &f{target}&c."),
            Map.entry("request-expired", "&cLa solicitud de teletransporte ha expirado."),
            Map.entry("request-expired-target", "&7La solicitud de &f{player} &7ha expirado."),
            Map.entry("request-denied", "&cSolicitud de teletransporte rechazada."),
            Map.entry("request-denied-target", "&eRechazaste la solicitud de &f{player}&e."),
            Map.entry("request-accepted", "&aTeletransporte completado."),
            Map.entry("request-accepted-target", "&aAceptaste la solicitud de &f{player}&a."),
            Map.entry("request-cancelled", "&eCancelaste tu solicitud de teletransporte."),
            Map.entry("request-cancelled-target", "&7{player} canceló su solicitud de teletransporte."),
            Map.entry("request-cancelled-disconnect", "&7La solicitud fue cancelada porque el jugador se desconectó."),
            Map.entry("target-disconnected", "&cEl jugador objetivo se desconectó."),
            Map.entry("requester-offline", "&cEl jugador que envió la solicitud ya no está conectado."),
            Map.entry("no-pending-request", "&cNo tienes solicitudes de teletransporte pendientes."),
            Map.entry("request-not-valid", "&cEsa solicitud ya no es válida o ha expirado."),
            Map.entry("no-outgoing-request", "&cNo tienes una solicitud enviada pendiente."),
            Map.entry("teleport-failed", "&cNo se pudo completar el teletransporte."),
            Map.entry("warmup-started",
                    "&eTeletransporte aceptado. Tienes &f{grace_seconds}s &epara detenerte; después no te muevas"
                            + " durante &f{movement_lock_seconds}s&e. Entrar en combate cancela la espera."),
            Map.entry("warmup-started-target", "&7{player} se teletransportará hacia ti en &f{seconds} segundos&7."),
            Map.entry("warmup-cancelled-move", "&cEl teletransporte fue cancelado porque te moviste."),
            Map.entry("warmup-cancelled-damage", "&cEl teletransporte fue cancelado porque recibiste daño."),
            Map.entry("warmup-cancelled-attack", "&cEl teletransporte fue cancelado porque atacaste."),
            Map.entry("warmup-cancelled-target", "&7El teletransporte de &f{player} &7fue cancelado."),
            Map.entry("invulnerable", "&aTienes protección temporal durante &f{seconds}s&a."),
            Map.entry("invulnerability-ended-on-attack", "&cTu protección temporal terminó al atacar."));

    static final List<String> DEFAULT_HELP = List.of(
            "&6&lTPA &7comandos:",
            "&e/tpa <jugador> &7- Solicita teletransportarte.",
            "&e/tpa aceptar &7- Acepta la solicitud más reciente.",
            "&e/tpa rechazar &7- Rechaza la solicitud más reciente.",
            "&e/tpa cancelar &7- Cancela tu solicitud enviada.");

    private final Supplier<ConfigurationSection> section;
    private final Function<String, String> colorizer;

    /**
     * @param section   the {@code tpa} section (read on every message, so reloads apply)
     * @param colorizer translates {@code &} color codes
     */
    TpaMessages(Supplier<ConfigurationSection> section, Function<String, String> colorizer) {
        this.section = section;
        this.colorizer = colorizer;
    }

    void send(CommandSender receiver, String key) {
        send(receiver, key, Map.of());
    }

    void send(CommandSender receiver, String key, Map<String, String> tokens) {
        if (receiver == null)
            return;
        String raw = text(key, tokens);
        if (!raw.isBlank())
            receiver.sendMessage(colorizer.apply(prefix() + raw));
    }

    void sendHelp(CommandSender receiver) {
        ConfigurationSection config = section.get();
        List<String> lines = config == null || !config.isList("help") ? DEFAULT_HELP : config.getStringList("help");
        for (String line : lines)
            receiver.sendMessage(colorizer.apply(line));
    }

    /** The message without prefix and colors, tokens applied; "" when disabled. */
    String text(String key, Map<String, String> tokens) {
        ConfigurationSection config = section.get();
        String raw = config == null ? null : config.getString("messages." + key);
        if (raw == null)
            raw = DEFAULTS.getOrDefault(key, "");
        return replaceTokens(raw, tokens);
    }

    private String prefix() {
        ConfigurationSection config = section.get();
        String prefix = config == null ? null : config.getString("prefix");
        return prefix == null ? DEFAULT_PREFIX : prefix;
    }

    static String replaceTokens(String raw, Map<String, String> tokens) {
        String text = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            text = text.replace("{" + entry.getKey() + "}", value).replace("%" + entry.getKey() + "%", value);
        }
        return text;
    }
}

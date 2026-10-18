package com.mdvcraft.mdvsocial.tpa;

import com.mdvcraft.mdvsocial.tpa.TpaCommand.Action;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpaRulesTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void settingsAreClamped() throws Exception {
        TpaSettings settings = TpaSettings.from(yaml("""
                tpa:
                  request-timeout-seconds: 1
                  cooldown-seconds: -3
                  teleport-delay-seconds: 3
                  movement-grace-seconds: 9
                """).getConfigurationSection("tpa"));
        assertEquals(5, settings.requestTimeoutSeconds());
        assertEquals(0, settings.cooldownSeconds());
        assertEquals(3, settings.movementGraceSeconds(), "grace cannot exceed the delay");
        assertEquals(0, settings.movementLockSeconds());
        assertEquals(TpaSettings.DEFAULTS, TpaSettings.from(null));
    }

    @Test
    void movingIsAllowedDuringTheGracePeriod() {
        TpaTeleporter.Warmup warmup = new TpaTeleporter.Warmup(new UUID(0, 1), new UUID(0, 2), 1_000);
        assertFalse(warmup.movementLocked(999));
        assertTrue(warmup.movementLocked(1_000));
    }

    @Test
    void turningTheHeadIsNotMoving() {
        Location from = new Location(null, 1, 64, 1, 0, 0);
        assertFalse(TpaTeleporter.changedPosition(from, new Location(null, 1, 64, 1, 90, 30)));
        assertTrue(TpaTeleporter.changedPosition(from, new Location(null, 1.2, 64, 1)));
    }

    @Test
    void deadlinesExpire() {
        PlayerDeadlines deadlines = new PlayerDeadlines();
        UUID player = new UUID(0, 1);
        deadlines.start(player, 0, 15_000);
        assertEquals(15, deadlines.remainingSeconds(player, 1));
        assertTrue(deadlines.isActive(player, 14_999));
        assertFalse(deadlines.isActive(player, 15_000));
        deadlines.start(player, 0, 0);
        assertFalse(deadlines.isActive(player, 0), "zero duration starts nothing");
    }

    @Test
    void commandsAreParsed() {
        assertEquals(new TpaCommand.Parsed(Action.ACCEPT, null), TpaCommand.parse("tpaccept", new String[0]));
        assertEquals(new TpaCommand.Parsed(Action.DENY, null), TpaCommand.parse("rechazartp", new String[0]));
        assertEquals(new TpaCommand.Parsed(Action.CANCEL, null), TpaCommand.parse("tpacancel", new String[0]));
        assertEquals(Action.HELP, TpaCommand.parse("tpa", new String[0]).action());
        assertEquals(new TpaCommand.Parsed(Action.REQUEST, "Steve"), TpaCommand.parse("tpa", new String[] {"Steve"}));
        assertEquals(Action.DENY, TpaCommand.parse("tpa", new String[] {"Denegar"}).action());
        assertEquals(new TpaCommand.Parsed(Action.ACCEPT_ONE, "abc"),
                TpaCommand.parse("tpa", new String[] {"aceptarsolicitud", "abc"}));
        assertEquals(Action.ACCEPT, TpaCommand.parse("tpa", new String[] {"aceptar-solicitud"}).action(),
                "without a UUID it answers the latest request");
    }

    @Test
    void messagesAcceptBothTokenStyles() throws Exception {
        YamlConfiguration config = yaml("""
                tpa:
                  messages:
                    request-sent: "Enviada a %target% ({seconds}s)"
                    cooldown: ""
                """);
        TpaMessages messages = new TpaMessages(() -> config.getConfigurationSection("tpa"), raw -> raw);
        assertEquals("Enviada a Steve (60s)",
                messages.text("request-sent", Map.of("target", "Steve", "seconds", "60")));
        assertEquals("", messages.text("cooldown", Map.of()));
        assertEquals("&cNo se pudo completar el teletransporte.", messages.text("teleport-failed", Map.of()));
    }

    @Test
    void mdvToolsConfigIsConverted() throws Exception {
        YamlConfiguration converted = MDVToolsTpaImport.convert(yaml("""
                messages:
                  prefix: "&6[MDV] "
                tpa:
                  cooldown-seconds: 30
                  allow-cross-world: false
                  messages:
                    request-sent: "&eEnviada a %target% (%seconds%s)"
                    request-received: "&6%player% quiere venir"
                """));
        assertEquals("&6[MDV] ", converted.getString("tpa.prefix"));
        assertEquals(30, converted.getInt("tpa.cooldown-seconds"));
        assertFalse(converted.getBoolean("tpa.allow-cross-world", true));
        assertEquals("&eEnviada a {target} ({seconds}s)", converted.getString("tpa.messages.request-sent"));
        assertNull(converted.get("tpa.messages.request-received"), "replaced by tpa.notice");
        assertNull(MDVToolsTpaImport.convert(yaml("otra: 1")));
    }
}

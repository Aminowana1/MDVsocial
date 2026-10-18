package com.mdvcraft.mdvsocial.quickaccept;

import com.mdvcraft.mdvsocial.quickaccept.SneakSequence.Result;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickAcceptTest {

    @Test
    void quickSneaksComplete() {
        SneakSequence sequence = new SneakSequence(2, 700, 30_000);
        assertEquals(Result.PROGRESS, sequence.register(1_000));
        assertEquals(Result.COMPLETE, sequence.register(1_400));
    }

    @Test
    void slowSneakStartsOver() {
        SneakSequence sequence = new SneakSequence(3, 500, 30_000);
        assertEquals(Result.PROGRESS, sequence.register(1_000));
        assertEquals(Result.PROGRESS, sequence.register(1_300));
        assertEquals(Result.PROGRESS, sequence.register(2_500), "gap too long: counts as the first again");
        assertEquals(Result.PROGRESS, sequence.register(2_800));
        assertEquals(Result.COMPLETE, sequence.register(3_100));
    }

    @Test
    void nothingCountsAfterTheWindow() {
        SneakSequence sequence = new SneakSequence(1, 500, 10_000);
        assertEquals(Result.EXPIRED, sequence.register(10_001));
        assertTrue(sequence.isExpired(10_001));
        assertFalse(sequence.isExpired(9_999));
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void typeOverridesFallBackToGlobalValues() throws Exception {
        YamlConfiguration config = yaml("""
                quick-accept:
                  enabled: true
                  sneaks-required: 2
                  window-seconds: 30
                  max-sneak-gap-millis: 700
                  trigger-sound: confirm
                  types:
                    party-invite:
                      sneaks-required: 3
                    teleport:
                      enabled: false
                """);
        QuickAcceptSettings party = QuickAcceptSettings.from(config.getConfigurationSection("quick-accept"),
                "party-invite");
        assertTrue(party.enabled());
        assertTrue(party.bedrockOnly());
        assertEquals(3, party.sneaksRequired());
        assertEquals(30, party.windowSeconds());
        assertEquals(700, party.maxGapMillis());
        assertEquals("confirm", party.triggerSound());

        assertFalse(QuickAcceptSettings.from(config.getConfigurationSection("quick-accept"), "teleport").enabled());
        assertEquals(2, QuickAcceptSettings.from(config.getConfigurationSection("quick-accept"), "otro")
                .sneaksRequired());
    }

    @Test
    void globalSwitchDisablesEveryType() throws Exception {
        YamlConfiguration config = yaml("""
                quick-accept:
                  enabled: false
                  types:
                    party-invite:
                      enabled: true
                """);
        assertFalse(QuickAcceptSettings.from(config.getConfigurationSection("quick-accept"), "party-invite")
                .enabled());
    }

    @Test
    void valuesAreClamped() throws Exception {
        YamlConfiguration config = yaml("""
                quick-accept:
                  sneaks-required: 0
                  window-seconds: 99999
                """);
        QuickAcceptSettings settings = QuickAcceptSettings.from(config.getConfigurationSection("quick-accept"), "x");
        assertEquals(1, settings.sneaksRequired());
        assertEquals(600, settings.windowSeconds());
    }
}

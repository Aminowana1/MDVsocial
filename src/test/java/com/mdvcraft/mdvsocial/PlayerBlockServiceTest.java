package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.PlayerBlockService.BlockResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerBlockServiceTest {

    @TempDir
    Path directory;

    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CAROL = new UUID(0, 3);

    private PlayerDataStore store() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("performance.profile-cache-size", 8);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("BlockTest"));
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        PlayerDataStore store = new PlayerDataStore(plugin, directory.resolve("players.db").toFile());
        store.open(null);
        return store;
    }

    @Test
    void blockIsOneDirectional() throws Exception {
        try (PlayerDataStore store = store()) {
            PlayerBlockService blocks = new PlayerBlockService(store, () -> 0);
            assertEquals(BlockResult.BLOCKED, blocks.block(ALICE, BOB));

            assertTrue(blocks.isBlocked(ALICE, BOB));
            assertFalse(blocks.isBlocked(BOB, ALICE));
            assertFalse(blocks.canContact(BOB, ALICE), "Bob cannot write to Alice");
            assertTrue(blocks.canContact(ALICE, BOB), "Alice can still write to Bob");
        }
    }

    @Test
    void blockResultsAndUnblock() throws Exception {
        try (PlayerDataStore store = store()) {
            PlayerBlockService blocks = new PlayerBlockService(store, () -> 1);
            assertEquals(BlockResult.SELF, blocks.block(ALICE, ALICE));
            assertEquals(BlockResult.BLOCKED, blocks.block(ALICE, BOB));
            assertEquals(BlockResult.ALREADY_BLOCKED, blocks.block(ALICE, BOB));
            assertEquals(BlockResult.LIMIT_REACHED, blocks.block(ALICE, CAROL));

            assertTrue(blocks.unblock(ALICE, BOB));
            assertFalse(blocks.unblock(ALICE, BOB));
            assertFalse(blocks.isBlocked(ALICE, BOB));
            assertFalse(blocks.isBlocked(null, BOB));
        }
    }

    @Test
    void blocksPersistAcrossRestarts() throws Exception {
        try (PlayerDataStore store = store()) {
            PlayerBlockService blocks = new PlayerBlockService(store, () -> 0);
            blocks.block(ALICE, BOB);
            blocks.block(ALICE, CAROL);
        }
        try (PlayerDataStore store = store()) {
            assertEquals(List.of(BOB, CAROL), new PlayerBlockService(store, () -> 0).blockedPlayers(ALICE));
        }
    }

    @Test
    void legacyImportRunsOnlyOnce() throws Exception {
        try (PlayerDataStore store = store()) {
            assertFalse(store.isMetaTrue("legacy_mail_blocks_migrated"));
            int imported = store.importBlocksOnce("legacy_mail_blocks_migrated",
                    Map.of(ALICE, List.of(BOB, ALICE), CAROL, List.of(ALICE)));
            assertEquals(2, imported, "self entries are skipped");
            assertTrue(store.isMetaTrue("legacy_mail_blocks_migrated"));
            assertEquals(-1, store.importBlocksOnce("legacy_mail_blocks_migrated", Map.of(BOB, List.of(ALICE))));

            PlayerBlockService blocks = new PlayerBlockService(store, () -> 0);
            assertTrue(blocks.isBlocked(ALICE, BOB));
            assertTrue(blocks.isBlocked(CAROL, ALICE));
            assertFalse(blocks.isBlocked(BOB, ALICE));
        }
    }

    @Test
    void mailboxLimitIsRemembered() throws Exception {
        try (PlayerDataStore store = store()) {
            assertEquals(-1, store.mailboxLimit(ALICE));
            store.setMailboxLimit(ALICE, 25);
            assertEquals(25, store.mailboxLimit(ALICE));
        }
        try (PlayerDataStore store = store()) {
            assertEquals(25, store.mailboxLimit(ALICE));
        }
    }

    @Test
    void legacyMailboxBlockListsAreReadable() throws Exception {
        File legacy = directory.resolve("mail-data.yml").toFile();
        Files.writeString(legacy.toPath(), """
                mailbox:
                  %s:
                    blocked: ['%s']
                    letters:
                      a:
                        message: hola
                  %s:
                    letters:
                      b:
                        message: sin bloqueos
                """.formatted(ALICE, BOB, CAROL));
        try (MailSqliteStore mail = new MailSqliteStore(directory.resolve("mail.db").toFile(),
                Logger.getLogger("BlockTest"))) {
            MailSqliteStore.TrackedMail data = mail.open(legacy);
            assertEquals(Map.of(ALICE.toString(), List.of(BOB.toString())), data.legacyBlockLists());

            data.set("mailbox." + ALICE + ".blocked", null);
            data.flush();
        }
        try (MailSqliteStore mail = new MailSqliteStore(directory.resolve("mail.db").toFile(),
                Logger.getLogger("BlockTest"))) {
            MailSqliteStore.TrackedMail data = mail.open(legacy);
            assertTrue(data.legacyBlockLists().isEmpty());
            assertEquals("hola", data.getString("mailbox." + ALICE + ".letters.a.message", ""));
        }
    }
}

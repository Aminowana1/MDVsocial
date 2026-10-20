package com.mdvcraft.mdvsocial.bedrock.menu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockMenuRepositoryTest {

    @Test
    void discoversMenusRecursivelyShallowestFirst(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("clan/sub"));
        Files.writeString(root.resolve("main.yml"), "title: a");
        Files.writeString(root.resolve("clan/clan.yml"), "title: b");
        Files.writeString(root.resolve("clan/sub/main.yml"), "title: duplicate");
        Files.writeString(root.resolve("clan/notes.txt"), "ignored");
        Files.writeString(root.resolve("Ayuda.YAML"), "title: c");

        List<String> found = BedrockMenuRepository.menuFiles(root).stream()
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .toList();

        assertEquals(List.of("Ayuda.YAML", "main.yml", "clan/clan.yml", "clan/sub/main.yml"), found);
    }
}

package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDuplicateKeysTest {

    @Test
    void findsRepeatedTopLevelSections() {
        Map<String, List<Integer>> found = ConfigDuplicateKeys.find(List.of(
                "scoreboard:",
                "  group-header: 'a'",
                "# scoreboard: comentario",
                "performance:",
                "  scoreboard: no cuenta, está indentado",
                "scoreboard:",
                "  group-header: 'b'"));
        assertEquals(Map.of("scoreboard", List.of(1, 6)), found);
    }

    @Test
    void bundledConfigHasNoRepeatedSections() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("config.yml"), StandardCharsets.UTF_8))) {
            assertTrue(ConfigDuplicateKeys.find(reader.lines().toList()).isEmpty());
        }
    }
}

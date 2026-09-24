package com.mdvcraft.mdvsocial;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Definitions are read once on enable/reload, never from disk while opening a menu. */
final class ProtectionMenuFiles {
    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("\\{([a-z_]+)}");
    static final String MAIN = "protes", OPTIONS = "protes_opciones", MEMBERS = "protes_miembros",
            SEARCH = "protes_buscar", CONFIRM = "protes_confirmar", INPUT = "protes_nombre";
    static final List<String> MENUS = List.of(MAIN, OPTIONS, MEMBERS, SEARCH, CONFIRM);
    private final JavaPlugin plugin;
    private Map<String, YamlConfiguration> definitions = Map.of();
    ProtectionMenuFiles(JavaPlugin plugin) { this.plugin = plugin; }

    void reload() {
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        for (boolean bedrock : List.of(false, true)) {
            List<String> ids = new ArrayList<>(MENUS);
            if (bedrock) ids.add(INPUT);
            for (String id : ids) {
                String path = (bedrock ? "MenusBedrock/" : "Menus/") + id + ".yml";
                YamlConfiguration defaults;
                try (InputStream stream = Objects.requireNonNull(plugin.getResource(path), path);
                     Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    defaults = YamlConfiguration.loadConfiguration(reader);
                } catch (IOException e) { throw new IllegalStateException("Cannot read " + path, e); }
                File file = new File(plugin.getDataFolder(), path);
                if (!file.exists()) plugin.saveResource(path, false);
                YamlConfiguration config = new YamlConfiguration();
                try {
                    config.load(file);
                    config.setDefaults(defaults);
                    if (!bedrock) validate(id, config);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Menú de protecciones inválido " + path + ": " + ex.getMessage()
                            + ". Se usan los valores originales sin sobrescribir tu archivo.");
                    config = defaults;
                }
                loaded.put(path, config);
            }
        }
        definitions = Map.copyOf(loaded);
    }

    YamlConfiguration get(boolean bedrock, String id) {
        return Objects.requireNonNull(definitions.get((bedrock ? "MenusBedrock/" : "Menus/") + id + ".yml"), id);
    }

    static void validate(String id, YamlConfiguration config) {
        int size = config.getInt("size");
        if (size < 9 || size > 54 || size % 9 != 0) throw new IllegalArgumentException("size debe ser 9..54, múltiplo de 9");
        Set<Integer> reserved = new HashSet<>();
        reserve(reserved, config.getInt("navigation.back.slot"), size);
        if (config.getBoolean("header.enabled", true)) reserve(reserved, config.getInt("header.slot"), size);
        if (id.equals(MEMBERS) || id.equals(SEARCH)) {
            reserve(reserved, config.getInt("navigation.previous.slot"), size);
            reserve(reserved, config.getInt("navigation.next.slot"), size);
            reserve(reserved, config.getInt("items." + (id.equals(MEMBERS) ? "add" : "name") + ".slot"), size);
        }
        List<Integer> slots = new ArrayList<>(config.getIntegerList("content-slots"));
        if (id.equals(MAIN)) slots.addAll(config.getIntegerList("overflow-slots"));
        if (slots.isEmpty()) throw new IllegalArgumentException("content-slots no puede estar vacío");
        if (id.equals(OPTIONS) && slots.size() < 3) throw new IllegalArgumentException("Las opciones necesitan tres slots");
        for (int slot : slots) reserve(reserved, slot, size);
    }
    private static void reserve(Set<Integer> used, int slot, int size) {
        if (slot < 0 || slot >= size || !used.add(slot)) throw new IllegalArgumentException("Slot fuera de rango o repetido: " + slot);
    }
    static List<Integer> slots(String id, YamlConfiguration config, int count) {
        List<Integer> slots = new ArrayList<>(config.getIntegerList("content-slots"));
        if (id.equals(MAIN) && count > slots.size()) slots.addAll(config.getIntegerList("overflow-slots"));
        return slots;
    }
    static boolean paginated(String id) { return id.equals(MEMBERS) || id.equals(SEARCH); }
    record Page(int index, int from, int to, int pages) {
        boolean previous() { return index > 0; }
        boolean next() { return index + 1 < pages; }
    }
    static Page page(int count, int capacity, int requested, boolean paginated) {
        if (capacity < 1) throw new IllegalArgumentException("No hay slots disponibles");
        if (!paginated && count > capacity) throw new IllegalArgumentException("Hay más protecciones que slots. Amplía content-slots/overflow-slots en Menus/protes.yml.");
        int pages = paginated ? Math.max(1, (count + capacity - 1) / capacity) : 1;
        int index = Math.max(0, Math.min(requested, pages - 1));
        return new Page(index, index * capacity, Math.min(count, (index + 1) * capacity), pages);
    }
    static String replace(String text, Map<String, String> values) {
        // One pass: item names/lore containing tokens must not trigger a second expansion.
        var matcher = TOKEN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result,
                java.util.regex.Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), matcher.group())));
        matcher.appendTail(result);
        return result.toString();
    }
}

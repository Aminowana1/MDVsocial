package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Navegador seguro y de solo lectura para las plantillas de MMOItems.
 *
 * <p>No abre ninguna GUI administrativa de MMOItems y nunca ejecuta comandos
 * de MMOItems. Las copias se construyen directamente desde la plantilla base,
 * sin tier aleatorio ni modificadores de plantilla.</p>
 */
public final class MMOItemsBrowserManager implements Listener {
    private static final int[] DEFAULT_CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final MDVSocialPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey itemIdKey;
    private final Map<String, List<String>> catalog = new LinkedHashMap<>();
    private final Map<String, ItemStack> baseItemCache = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ItemStack> entry) {
            return size() > Math.max(1, Math.min(4096, plugin.getConfig().getInt("performance.item-cache-size", 256)));
        }
    };
    private final Set<String> failedBaseItems = new LinkedHashSet<>();

    public MMOItemsBrowserManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "mmo_browser_action");
        this.typeKey = new NamespacedKey(plugin, "mmo_browser_type");
        this.itemIdKey = new NamespacedKey(plugin, "mmo_browser_item_id");
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        catalog.clear();
        baseItemCache.clear();
        failedBaseItems.clear();
        if (!enabled() || !isMMOItemsAvailable()) return;

        Map<String, Set<String>> discovered = new HashMap<>();
        discoverFromFiles(discovered);
        if (discovered.isEmpty()) discoverFromApi(discovered);

        List<String> typeOrder = normalizedList(plugin.getConfig().getStringList("mmoitems-browser.type-order"));
        Set<String> excludedTypes = new LinkedHashSet<>(normalizedList(plugin.getConfig().getStringList("mmoitems-browser.excluded-types")));
        Set<String> excludedItems = new LinkedHashSet<>(normalizedList(plugin.getConfig().getStringList("mmoitems-browser.excluded-items")));

        List<String> types = new ArrayList<>(discovered.keySet());
        types.removeIf(excludedTypes::contains);
        types.sort(Comparator
                .comparingInt((String type) -> {
                    int index = typeOrder.indexOf(type);
                    return index < 0 ? Integer.MAX_VALUE : index;
                })
                .thenComparing(this::typeDisplayPlain, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(String.CASE_INSENSITIVE_ORDER));

        for (String type : types) {
            List<String> ids = discovered.getOrDefault(type, Collections.emptySet()).stream()
                    .map(this::normalize)
                    .filter(id -> !id.isBlank())
                    .filter(id -> !excludedItems.contains(type + ":" + id))
                    // Algunos formatos de MMOItems incluyen claves auxiliares en YAML.
                    // Solo publicamos entradas que realmente pueden convertirse en un objeto.
                    .filter(id -> buildBaseItemWithoutModifiers(type, id) != null)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            if (!ids.isEmpty()) catalog.put(type, new ArrayList<>(ids));
        }

        plugin.getLogger().info("Biblioteca segura de MMOItems cargada: " + catalog.size()
                + " tipos y " + catalog.values().stream().mapToInt(List::size).sum() + " objetos.");
    }

    public void open(Player player) {
        if (!enabled()) {
            message(player, "disabled", "&cLa biblioteca de objetos está desactivada.");
            return;
        }
        if (!hasPermission(player)) {
            plugin.sendConfiguredMessage(player, "no-permission");
            return;
        }
        if (!isMMOItemsAvailable()) {
            message(player, "mmoitems-missing", "&cMMOItems no está disponible.");
            return;
        }
        if (catalog.isEmpty()) reload();
        if (plugin.isBedrockPlayer(player)) {
            openBedrockTypes(player, 0);
            return;
        }
        openTypes(player, 1);
    }

    private void openBedrockTypes(Player player, int requestedPage) {
        List<String> types = new ArrayList<>(catalog.keySet());
        int perPage = Math.max(4, Math.min(15, plugin.getConfig().getInt("bedrock.dynamic-page-size", 8)));
        int maxPage = Math.max(0, (int) Math.ceil(types.size() / (double) perPage) - 1);
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        int start = page * perPage;
        int end = Math.min(types.size(), start + perPage);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(config("titles.types", "&8Biblioteca de MMOItems &7({page}/{max_page})")
                        .replace("{page}", String.valueOf(page + 1))
                        .replace("{max_page}", String.valueOf(maxPage + 1))))
                .content(color("&7Selecciona una categoría. La extracción sigue usando objetos base sin modificadores."));
        List<Runnable> actions = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String type = types.get(i);
            builder.button(typeDisplay(type));
            actions.add(() -> openBedrockItems(player, type, 0));
        }
        if (page > 0) {
            builder.button(color("&ePágina anterior"));
            actions.add(() -> openBedrockTypes(player, page - 1));
        }
        if (page < maxPage) {
            builder.button(color("&aPágina siguiente"));
            actions.add(() -> openBedrockTypes(player, page + 1));
        }
        builder.button(color("&6Volver a administración"));
        actions.add(() -> plugin.openAdminMenu(player));
        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) actions.get(index).run();
        }));
        sendBedrockForm(player, builder);
    }

    private void openBedrockItems(Player player, String rawType, int requestedPage) {
        String type = normalize(rawType);
        List<String> ids = catalog.get(type);
        if (ids == null || ids.isEmpty()) {
            message(player, "empty-type", "&cNo se encontraron objetos en esa categoría.");
            openBedrockTypes(player, 0);
            return;
        }
        int perPage = Math.max(4, Math.min(15, plugin.getConfig().getInt("bedrock.dynamic-page-size", 8)));
        int maxPage = Math.max(0, (int) Math.ceil(ids.size() / (double) perPage) - 1);
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        int start = page * perPage;
        int end = Math.min(ids.size(), start + perPage);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(typeDisplay(type) + " &8(" + (page + 1) + "/" + (maxPage + 1) + ")"))
                .content(color("&7Toca un objeto para elegir cuánto extraer."));
        List<Runnable> actions = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            ItemStack preview = buildBaseItemWithoutModifiers(type, id);
            String display = bedrockItemDisplay(preview, id);
            builder.button(color(display + "\n&8" + type + ":" + id));
            actions.add(() -> openBedrockItemActions(player, type, id, page));
        }
        if (page > 0) {
            builder.button(color("&ePágina anterior"));
            actions.add(() -> openBedrockItems(player, type, page - 1));
        }
        if (page < maxPage) {
            builder.button(color("&aPágina siguiente"));
            actions.add(() -> openBedrockItems(player, type, page + 1));
        }
        builder.button(color("&6Volver a categorías"));
        actions.add(() -> openBedrockTypes(player, 0));
        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) actions.get(index).run();
        }));
        sendBedrockForm(player, builder);
    }

    private void openBedrockItemActions(Player player, String type, String id, int returnPage) {
        ItemStack preview = buildBaseItemWithoutModifiers(type, id);
        if (preview == null || preview.getType().isAir()) {
            message(player, "item-build-failed", "&cNo se pudo generar ese objeto.");
            openBedrockItems(player, type, returnPage);
            return;
        }
        String display = bedrockItemDisplay(preview, id);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(display))
                .content(color("&8Tipo: &7" + type + "\n&8ID: &7" + id
                        + "\n&8Generación: &7base, sin modificadores"))
                .button(color("&aObtener 1"));
        List<Runnable> actions = new ArrayList<>();
        actions.add(() -> {
            give(player, type, id, false);
            openBedrockItemActions(player, type, id, returnPage);
        });
        if (preview.getMaxStackSize() > 1
                && plugin.getConfig().getBoolean("mmoitems-browser.extraction.shift-click-enabled", true)) {
            int amount = Math.min(Math.max(1,
                    plugin.getConfig().getInt("mmoitems-browser.extraction.shift-click-amount", 64)),
                    preview.getMaxStackSize());
            builder.button(color("&eObtener " + amount));
            actions.add(() -> {
                give(player, type, id, true);
                openBedrockItemActions(player, type, id, returnPage);
            });
        }
        builder.button(color("&6Volver a objetos"));
        actions.add(() -> openBedrockItems(player, type, returnPage));
        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) actions.get(index).run();
        }));
        sendBedrockForm(player, builder);
    }

    private String bedrockItemDisplay(ItemStack item, String id) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName() != null && !meta.getDisplayName().isBlank())
                return meta.getDisplayName();
        }
        return pretty(id);
    }

    private boolean sendBedrockForm(Player player, SimpleForm.Builder builder) {
        try {
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar la biblioteca Bedrock a " + player.getName()
                    + ": " + ex.getMessage());
            return false;
        }
    }

    private void openTypes(Player player, int requestedPage) {
        List<String> types = new ArrayList<>(catalog.keySet());
        List<Integer> slots = contentSlots();
        int pages = Math.max(1, (int) Math.ceil(types.size() / (double) slots.size()));
        int page = Math.max(1, Math.min(pages, requestedPage));

        BrowserHolder holder = new BrowserHolder(Screen.TYPES, page, pages, "");
        Inventory inventory = Bukkit.createInventory(holder, menuSize(), color(config("titles.types", "&8Biblioteca de MMOItems &7({page}/{max_page})")
                .replace("{page}", String.valueOf(page))
                .replace("{max_page}", String.valueOf(pages))));
        holder.inventory = inventory;
        fill(inventory);

        int start = (page - 1) * slots.size();
        for (int i = 0; i < slots.size() && start + i < types.size(); i++) {
            String type = types.get(start + i);
            inventory.setItem(slots.get(i), typeButton(type));
        }
        renderNavigation(inventory, page, pages, true);
        player.openInventory(inventory);
    }

    private void openItems(Player player, String rawType, int requestedPage) {
        String type = normalize(rawType);
        List<String> ids = catalog.get(type);
        if (ids == null || ids.isEmpty()) {
            message(player, "empty-type", "&cNo se encontraron objetos en esa categoría.");
            openTypes(player, 1);
            return;
        }

        List<Integer> slots = contentSlots();
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) slots.size()));
        int page = Math.max(1, Math.min(pages, requestedPage));
        BrowserHolder holder = new BrowserHolder(Screen.ITEMS, page, pages, type);
        Inventory inventory = Bukkit.createInventory(holder, menuSize(), color(config("titles.items", "&8{type} &7({page}/{max_page})")
                .replace("{type}", typeDisplay(type))
                .replace("{page}", String.valueOf(page))
                .replace("{max_page}", String.valueOf(pages))));
        holder.inventory = inventory;
        fill(inventory);

        int start = (page - 1) * slots.size();
        for (int i = 0; i < slots.size() && start + i < ids.size(); i++) {
            String id = ids.get(start + i);
            ItemStack preview = itemButton(type, id);
            if (preview != null) inventory.setItem(slots.get(i), preview);
        }
        renderNavigation(inventory, page, pages, false);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof BrowserHolder holder)) return;
        event.setCancelled(true);

        if (!hasPermission(player)) {
            player.closeInventory();
            plugin.sendConfiguredMessage(player, "no-permission");
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "OPEN_TYPE" -> openItems(player, pdc.get(typeKey, PersistentDataType.STRING), 1);
            case "PREVIOUS" -> {
                if (holder.screen == Screen.TYPES) openTypes(player, holder.page - 1);
                else openItems(player, holder.type, holder.page - 1);
            }
            case "NEXT" -> {
                if (holder.screen == Screen.TYPES) openTypes(player, holder.page + 1);
                else openItems(player, holder.type, holder.page + 1);
            }
            case "BACK" -> {
                if (holder.screen == Screen.ITEMS) openTypes(player, 1);
                else plugin.openAdminMenu(player);
            }
            case "CLOSE" -> player.closeInventory();
            case "GIVE" -> {
                if (!event.isLeftClick()) return;
                String type = pdc.get(typeKey, PersistentDataType.STRING);
                String id = pdc.get(itemIdKey, PersistentDataType.STRING);
                give(player, type, id, event.isShiftClick());
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BrowserHolder) event.setCancelled(true);
    }

    private void give(Player player, String rawType, String rawId, boolean shiftClick) {
        String type = normalize(rawType);
        String id = normalize(rawId);
        if (!catalog.getOrDefault(type, List.of()).contains(id)) {
            message(player, "item-not-found", "&cEse objeto ya no está disponible.");
            return;
        }
        ItemStack item = buildBaseItemWithoutModifiers(type, id);
        if (item == null || item.getType().isAir()) {
            message(player, "item-build-failed", "&cNo se pudo generar ese objeto.");
            return;
        }

        int amount = 1;
        if (shiftClick && plugin.getConfig().getBoolean("mmoitems-browser.extraction.shift-click-enabled", true)) {
            int configured = Math.max(1, plugin.getConfig().getInt("mmoitems-browser.extraction.shift-click-amount", 64));
            amount = Math.min(configured, Math.max(1, item.getMaxStackSize()));
        }
        item.setAmount(amount);

        if (!canFit(player.getInventory(), item)) {
            message(player, "inventory-full", "&cNo tienes espacio suficiente en el inventario.");
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            message(player, "inventory-full", "&cNo tienes espacio suficiente en el inventario.");
            return;
        }

        String raw = config("messages.received", "&aObtuviste &e{amount}x &f{type}:{id} &asin modificadores aleatorios.");
        player.sendMessage(color(prefix() + raw
                .replace("{amount}", String.valueOf(amount))
                .replace("{type}", type)
                .replace("{id}", id)));
        logExtraction(player, type, id, amount);
    }

    private ItemStack typeButton(String type) {
        List<String> ids = catalog.getOrDefault(type, List.of());
        ItemStack icon = null;
        String configuredMaterial = plugin.getConfig().getString("mmoitems-browser.type-overrides." + type + ".material", "");
        Material material = Material.matchMaterial(configuredMaterial == null ? "" : configuredMaterial);
        if (material != null && !material.isAir()) icon = new ItemStack(material);
        if (icon == null && !ids.isEmpty()) icon = buildBaseItemWithoutModifiers(type, ids.get(0));
        if (icon == null || icon.getType().isAir()) icon = new ItemStack(Material.CHEST);
        icon = icon.clone();
        icon.setAmount(1);

        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        meta.setDisplayName(color("&6&l" + typeDisplay(type)));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Tipo: &f" + type));
        lore.add(color("&7Objetos: &f" + ids.size()));
        lore.add("");
        lore.add(color("&eClic para abrir la categoría."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "OPEN_TYPE");
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack itemButton(String type, String id) {
        ItemStack preview = buildBaseItemWithoutModifiers(type, id);
        if (preview == null || preview.getType().isAir()) return null;
        preview = preview.clone();
        preview.setAmount(1);
        ItemMeta meta = preview.getItemMeta();
        if (meta == null) return preview;

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        lore.add("");
        lore.add(color("&8Tipo: &7" + type));
        lore.add(color("&8ID: &7" + id));
        lore.add(color("&8Generación: &7base, sin modificadores"));
        lore.add("");
        lore.add(color("&eClic izquierdo: &fobtener 1"));
        if (preview.getMaxStackSize() > 1 && plugin.getConfig().getBoolean("mmoitems-browser.extraction.shift-click-enabled", true)) {
            int amount = Math.min(Math.max(1, plugin.getConfig().getInt("mmoitems-browser.extraction.shift-click-amount", 64)), preview.getMaxStackSize());
            lore.add(color("&eShift + clic izquierdo: &fobtener " + amount));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "GIVE");
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        preview.setItemMeta(meta);
        return preview;
    }

    private void renderNavigation(Inventory inventory, int page, int pages, boolean root) {
        if (page > 1) inventory.setItem(navSlot("previous-slot", 45), navigationItem("PREVIOUS", Material.ARROW,
                config("navigation.previous-name", "&ePágina anterior"), List.of("&7Página " + (page - 1) + " de " + pages)));
        inventory.setItem(navSlot("back-slot", 49), navigationItem("BACK", Material.PLAYER_HEAD,
                root ? config("navigation.back-admin-name", "&6Volver a administración") : config("navigation.back-types-name", "&6Volver a categorías"), List.of()));
        if (page < pages) inventory.setItem(navSlot("next-slot", 53), navigationItem("NEXT", Material.ARROW,
                config("navigation.next-name", "&aPágina siguiente"), List.of("&7Página " + (page + 1) + " de " + pages)));
        int closeSlot = navSlot("close-slot", -1);
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            inventory.setItem(closeSlot, navigationItem("CLOSE", Material.BARRIER, config("navigation.close-name", "&cCerrar"), List.of()));
        }
    }

    private ItemStack navigationItem(String action, Material fallback, String name, List<String> rawLore) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("mmoitems-browser.navigation." + action.toLowerCase(Locale.ROOT) + "-material", fallback.name()));
        if (material == null || material.isAir()) material = fallback;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        if (!rawLore.isEmpty()) meta.setLore(rawLore.stream().map(this::color).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        if (!plugin.getConfig().getBoolean("mmoitems-browser.fill.enabled", true)) return;
        Material material = Material.matchMaterial(plugin.getConfig().getString("mmoitems-browser.fill.material", "BLACK_STAINED_GLASS_PANE"));
        if (material == null || material.isAir()) material = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private boolean canFit(Inventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        int max = Math.max(1, item.getMaxStackSize());
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) {
                remaining -= max;
            } else if (current.isSimilar(item)) {
                remaining -= Math.max(0, max - current.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private void discoverFromFiles(Map<String, Set<String>> discovered) {
        Plugin mmoItems = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null) return;
        File itemFolder = new File(mmoItems.getDataFolder(), "item");
        if (!itemFolder.isDirectory()) return;
        File[] children = itemFolder.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                String type = normalize(child.getName());
                for (File yaml : recursiveYamlFiles(child)) readIds(type, yaml, discovered);
            } else if (isYaml(child)) {
                String name = child.getName();
                String type = normalize(name.substring(0, name.lastIndexOf('.')));
                readIds(type, child, discovered);
            }
        }
    }

    private void readIds(String type, File file, Map<String, Set<String>> discovered) {
        if (type.isBlank()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("items");
        Set<String> keys = section == null ? yaml.getKeys(false) : section.getKeys(false);
        for (String rawId : keys) {
            String id = normalize(rawId);
            if (id.isBlank()) continue;
            discovered.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(id);
        }
    }

    private void discoverFromApi(Map<String, Set<String>> discovered) {
        try {
            Object mmo = mmoItemsPluginObject();
            if (mmo == null) return;
            Object typeManager = mmo.getClass().getMethod("getTypes").invoke(mmo);
            Object allTypes = typeManager.getClass().getMethod("getAll").invoke(typeManager);
            if (!(allTypes instanceof Iterable<?> iterable)) return;
            Object templates = mmo.getClass().getMethod("getTemplates").invoke(mmo);

            for (Object typeObject : iterable) {
                String type = objectId(typeObject);
                if (type.isBlank()) continue;
                for (Method method : templates.getClass().getMethods()) {
                    if (!(method.getName().equals("getTemplates") || method.getName().equals("getAll")) || method.getParameterCount() != 1) continue;
                    if (!method.getParameterTypes()[0].isInstance(typeObject)) continue;
                    Object result = method.invoke(templates, typeObject);
                    Collection<?> values = values(result);
                    for (Object template : values) {
                        String id = objectId(template);
                        if (!id.isBlank()) discovered.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(id);
                    }
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enumerar MMOItems mediante la API: " + ex.getClass().getSimpleName());
        }
    }

    private ItemStack buildBaseItemWithoutModifiers(String rawType, String rawId) {
        String type = normalize(rawType);
        String id = normalize(rawId);
        String key = type + ":" + id;
        if (failedBaseItems.contains(key)) return null;
        ItemStack cached = baseItemCache.get(key);
        if (cached != null) return cached.clone();

        ItemStack built = createBaseItemWithoutModifiers(type, id);
        if (built == null || built.getType().isAir()) {
            failedBaseItems.add(key);
            return null;
        }
        ItemStack normalized = built.clone();
        normalized.setAmount(1);
        baseItemCache.put(key, normalized);
        return normalized.clone();
    }

    private ItemStack createBaseItemWithoutModifiers(String rawType, String rawId) {
        if (!isMMOItemsAvailable()) return null;
        String typeId = normalize(rawType);
        String itemId = normalize(rawId);
        try {
            Object mmo = mmoItemsPluginObject();
            if (mmo == null) return null;
            Object types = mmo.getClass().getMethod("getTypes").invoke(mmo);
            Object type = types.getClass().getMethod("get", String.class).invoke(types, typeId);
            if (type == null) return null;

            // Preferimos el constructor que MMOItems usa para omitir por completo
            // el grupo de modificadores aleatorios de la plantilla.
            try {
                Object templates = mmo.getClass().getMethod("getTemplates").invoke(mmo);
                Object template = null;
                for (Method method : templates.getClass().getMethods()) {
                    if (!method.getName().equals("getTemplate") || method.getParameterCount() != 2) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0].isInstance(type) && params[1].equals(String.class)) {
                        template = method.invoke(templates, type, itemId);
                        break;
                    }
                }
                if (template != null) {
                    Class<?> builderClass = Class.forName("net.Indyuce.mmoitems.api.item.build.MMOItemBuilder");
                    Class<?> templateClass = Class.forName("net.Indyuce.mmoitems.api.item.template.MMOItemTemplate");
                    Class<?> tierClass = Class.forName("net.Indyuce.mmoitems.api.ItemTier");
                    Object builder = builderClass.getConstructor(templateClass, int.class, tierClass, boolean.class)
                            .newInstance(template, 0, null, true);
                    Object mmoItem = builderClass.getMethod("build").invoke(builder);
                    Object stackBuilder = mmoItem.getClass().getMethod("newBuilder").invoke(mmoItem);
                    Object built = stackBuilder.getClass().getMethod("build").invoke(stackBuilder);
                    if (built instanceof ItemStack stack) return stack;
                }
            } catch (Throwable ignored) {
                // La API cambia entre snapshots. El fallback oficial de dos
                // argumentos también genera un item sin tier aleatorio.
            }

            for (Method method : mmo.getClass().getMethods()) {
                if (!method.getName().equals("getItem") || method.getParameterCount() != 2) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params[0].isInstance(type) && params[1].equals(String.class)) {
                    Object built = method.invoke(mmo, type, itemId);
                    if (built instanceof ItemStack stack) return stack;
                }
            }
        } catch (Throwable ex) {
            if (plugin.getConfig().getBoolean("mmoitems-browser.debug", false)) {
                plugin.getLogger().warning("No se pudo construir " + typeId + ":" + itemId + " - " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        return null;
    }

    private Object mmoItemsPluginObject() throws ReflectiveOperationException {
        Class<?> clazz = Class.forName("net.Indyuce.mmoitems.MMOItems");
        Field field = clazz.getField("plugin");
        return field.get(null);
    }

    private boolean isMMOItemsAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("MMOItems");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mmoitems-browser.enabled", true);
    }

    private boolean hasPermission(Player player) {
        String permission = plugin.getConfig().getString("mmoitems-browser.permission", "mdvsocial.item-browser");
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private int menuSize() {
        int size = plugin.getConfig().getInt("mmoitems-browser.menu-size", 54);
        size = Math.max(9, Math.min(54, size));
        return size % 9 == 0 ? size : ((size / 9) + 1) * 9;
    }

    private List<Integer> contentSlots() {
        int size = menuSize();
        List<Integer> configured = plugin.getConfig().getIntegerList("mmoitems-browser.content-slots");
        List<Integer> valid = configured.stream()
                .filter(slot -> slot >= 0 && slot < size)
                .distinct()
                .toList();
        if (!valid.isEmpty()) return valid;

        List<Integer> defaults = new ArrayList<>();
        for (int slot : DEFAULT_CONTENT_SLOTS) {
            if (slot >= 0 && slot < size) defaults.add(slot);
        }
        // Evita una división por cero incluso si se configura un inventario muy pequeño.
        if (defaults.isEmpty()) defaults.add(0);
        return defaults;
    }

    boolean isBrowserInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof BrowserHolder;
    }

    private int navSlot(String key, int fallback) {
        return plugin.getConfig().getInt("mmoitems-browser.navigation." + key, fallback);
    }

    private String typeDisplay(String type) {
        String override = plugin.getConfig().getString("mmoitems-browser.type-overrides." + type + ".name", "");
        if (override != null && !override.isBlank()) return color(override);
        try {
            Object mmo = mmoItemsPluginObject();
            Object types = mmo.getClass().getMethod("getTypes").invoke(mmo);
            Object typeObject = types.getClass().getMethod("get", String.class).invoke(types, type);
            if (typeObject != null) {
                for (String methodName : List.of("getName", "getDisplayName")) {
                    try {
                        Object value = typeObject.getClass().getMethod(methodName).invoke(typeObject);
                        if (value != null && !String.valueOf(value).isBlank()) return color(String.valueOf(value));
                    } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable ignored) { }
        return pretty(type);
    }

    private String typeDisplayPlain(String type) {
        return ChatColor.stripColor(typeDisplay(type));
    }

    private void logExtraction(Player player, String type, String id, int amount) {
        String line = player.getName() + " obtuvo " + amount + "x " + type + ":" + id + " desde la biblioteca segura.";
        if (plugin.getConfig().getBoolean("mmoitems-browser.logging.console", true)) plugin.getLogger().info(line);
        if (!plugin.getConfig().getBoolean("mmoitems-browser.logging.file", true)) return;
        File file = new File(plugin.getDataFolder(), "logs/item-browser.log");
        try {
            File parent = file.getParentFile();
            if (!parent.exists()) parent.mkdirs();
            Files.writeString(file.toPath(), Instant.now() + " | " + player.getUniqueId() + " | " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir item-browser.log: " + ex.getMessage());
        }
    }

    private void message(Player player, String key, String fallback) {
        player.sendMessage(color(prefix() + config("messages." + key, fallback)));
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "&6&l[&5&lMDVSocial&6&l]&4> &r");
    }

    private String config(String path, String fallback) {
        return plugin.getConfig().getString("mmoitems-browser." + path, fallback);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private List<String> normalizedList(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::normalize).filter(value -> !value.isBlank()).toList();
    }

    private String pretty(String value) {
        String[] words = normalize(value).toLowerCase(Locale.ROOT).split("_");
        List<String> result = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) continue;
            result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", result);
    }

    private String objectId(Object object) {
        if (object == null) return "";
        for (String methodName : List.of("getId", "getID")) {
            try {
                Object value = object.getClass().getMethod(methodName).invoke(object);
                if (value != null) return normalize(String.valueOf(value));
            } catch (Throwable ignored) { }
        }
        return "";
    }

    private Collection<?> values(Object result) {
        if (result instanceof Map<?, ?> map) return map.values();
        if (result instanceof Collection<?> collection) return collection;
        if (result instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object value : iterable) list.add(value);
            return list;
        }
        return List.of();
    }

    private List<File> recursiveYamlFiles(File folder) {
        List<File> result = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return result;
        for (File file : files) {
            if (file.isDirectory()) result.addAll(recursiveYamlFiles(file));
            else if (isYaml(file)) result.add(file);
        }
        return result;
    }

    private boolean isYaml(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private enum Screen { TYPES, ITEMS }

    private static final class BrowserHolder implements InventoryHolder {
        private final Screen screen;
        private final int page;
        @SuppressWarnings("unused")
        private final int maxPage;
        private final String type;
        private Inventory inventory;

        private BrowserHolder(Screen screen, int page, int maxPage, String type) {
            this.screen = screen;
            this.page = page;
            this.maxPage = maxPage;
            this.type = type == null ? "" : type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

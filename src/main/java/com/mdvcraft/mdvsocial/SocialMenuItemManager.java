package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Item permanente del menu social de MDVCRAFT.
 *
 * Protege un ItemStack marcado con PersistentDataContainer para que quede fijo
 * en un slot de la hotbar y ejecute /social con click derecho.
 */
public final class SocialMenuItemManager implements Listener {

    private static final String CONFIG_PATH = "social-menu-item";

    private final MDVSocialPlugin plugin;
    private final NamespacedKey itemKey;
    private BukkitTask checkTask;

    private boolean enabled;
    private int slot;
    private String command;
    private int checkIntervalSeconds;
    private boolean dropReplacedItem;
    private boolean removeExtraCopies;
    private boolean removeWhenDisabled;
    private Material material;
    private String name;
    private List<String> lore;
    private boolean glowing;
    private int customModelData;

    public SocialMenuItemManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "social_menu_item");
    }

    public void enable() {
        reloadSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTask();
        if (enabled) {
            Bukkit.getScheduler().runTaskLater(plugin, this::syncOnlinePlayers, 20L);
        }
    }

    public void disable() {
        stopTask();
        HandlerList.unregisterAll(this);
    }

    public void reload() {
        boolean wasEnabled = enabled;
        reloadSettings();
        stopTask();
        startTask();

        if (enabled) {
            syncOnlinePlayers();
        } else if (wasEnabled && removeWhenDisabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeAllMenuItems(player);
            }
        }
    }

    private void reloadSettings() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(CONFIG_PATH + ".enabled", true);
        slot = Math.max(0, Math.min(35, config.getInt(CONFIG_PATH + ".slot", 8)));
        command = normalizeCommand(config.getString(CONFIG_PATH + ".command", "social"));
        checkIntervalSeconds = Math.max(1, config.getInt(CONFIG_PATH + ".check-interval-seconds", 5));
        dropReplacedItem = config.getBoolean(CONFIG_PATH + ".drop-replaced-item", true);
        removeExtraCopies = config.getBoolean(CONFIG_PATH + ".remove-extra-copies", true);
        removeWhenDisabled = config.getBoolean(CONFIG_PATH + ".remove-when-disabled", false);
        material = parseMaterial(config.getString(CONFIG_PATH + ".material", "COMPASS"));
        name = color(config.getString(CONFIG_PATH + ".name", "&6&lSello del Viajero"));
        lore = colorList(config.getStringList(CONFIG_PATH + ".lore"));
        glowing = config.getBoolean(CONFIG_PATH + ".glowing", true);
        customModelData = Math.max(0, config.getInt(CONFIG_PATH + ".custom-model-data", 0));
    }

    private void startTask() {
        if (!enabled) return;
        long interval = Math.max(20L, checkIntervalSeconds * 20L);
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncOnlinePlayers, interval, interval);
    }

    private void stopTask() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    private void syncOnlinePlayers() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureMenuItem(player);
        }
    }

    private void ensureMenuItem(Player player) {
        if (!enabled || player == null || !player.isOnline()) return;

        PlayerInventory inventory = player.getInventory();
        ItemStack wanted = createMenuItem();
        ItemStack current = inventory.getItem(slot);

        if (current == null || current.getType().isAir()) {
            inventory.setItem(slot, wanted);
        } else if (!isMenuItem(current)) {
            if (dropReplacedItem) {
                player.getWorld().dropItemNaturally(player.getLocation(), current.clone());
            }
            inventory.setItem(slot, wanted);
        } else if (!isSameMenuItem(current, wanted)) {
            inventory.setItem(slot, wanted);
        }

        if (removeExtraCopies) {
            removeExtraMenuItems(player);
        }
    }

    private ItemStack createMenuItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);

        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE
        );

        if (glowing) {
            applyGlowing(meta);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void applyGlowing(ItemMeta meta) {
        try {
            Method method = meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class);
            method.invoke(meta, Boolean.TRUE);
            return;
        } catch (Throwable ignored) {
            // Compatibilidad con APIs antiguas.
        }

        // Si la API no soporta glint override, simplemente queda sin brillo.
    }

    private boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Byte value = pdc.get(itemKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private boolean isSameMenuItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        if (!a.hasItemMeta() || !b.hasItemMeta()) return false;
        ItemMeta ma = a.getItemMeta();
        ItemMeta mb = b.getItemMeta();
        return String.valueOf(ma.getDisplayName()).equals(String.valueOf(mb.getDisplayName()))
                && String.valueOf(ma.getLore()).equals(String.valueOf(mb.getLore()))
                && isMenuItem(a)
                && isMenuItem(b);
    }

    private void removeExtraMenuItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == slot) continue;
            ItemStack item = inventory.getItem(i);
            if (isMenuItem(item)) {
                inventory.setItem(i, null);
            }
        }

        if (isMenuItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    private void removeAllMenuItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isMenuItem(inventory.getItem(i))) {
                inventory.setItem(i, null);
            }
        }
        if (isMenuItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureMenuItem(event.getPlayer()), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureMenuItem(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled) return;
        event.getDrops().removeIf(this::isMenuItem);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!enabled) return;
        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> ensureMenuItem(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!enabled) return;
        if (isMenuItem(event.getMainHandItem()) || isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> ensureMenuItem(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isMenuItem(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensureMenuItem(player);
            Bukkit.dispatchCommand(player, command);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean touchedMenuItem = isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor());
        boolean lockedSlotTouched = isPlayerInventorySlot(event, slot);
        boolean hotbarSwapToLockedSlot = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == slot;

        if (touchedMenuItem || lockedSlotTouched || hotbarSwapToLockedSlot) {
            event.setCancelled(true);
            cleanupIllegalMenuItemsFromView(event, player);
            Bukkit.getScheduler().runTask(plugin, () -> ensureMenuItem(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreative(InventoryCreativeEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor()) || event.getSlot() == slot) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> ensureMenuItem(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean draggingMenuItem = isMenuItem(event.getOldCursor());
        boolean touchesLockedSlot = event.getRawSlots().stream().anyMatch(raw -> raw == rawSlotOfPlayerInventorySlot(event.getView().getTopInventory(), slot));

        if (draggingMenuItem || touchesLockedSlot) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> ensureMenuItem(player));
        }
    }

    private boolean isPlayerInventorySlot(InventoryClickEvent event, int playerSlot) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked.getType() != InventoryType.PLAYER) return false;
        return event.getSlot() == playerSlot;
    }

    private int rawSlotOfPlayerInventorySlot(Inventory topInventory, int playerSlot) {
        return topInventory.getSize() + playerSlot;
    }

    private void cleanupIllegalMenuItemsFromView(InventoryClickEvent event, Player player) {
        if (!removeExtraCopies) return;
        Inventory top = event.getView().getTopInventory();
        for (int i = 0; i < top.getSize(); i++) {
            ItemStack item = top.getItem(i);
            if (isMenuItem(item)) {
                top.setItem(i, null);
            }
        }
        removeExtraMenuItems(player);
    }

    private Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return Material.COMPASS;
        Material parsed = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return parsed == null ? Material.COMPASS : parsed;
    }

    private String normalizeCommand(String raw) {
        if (raw == null || raw.isBlank()) return "social";
        String out = raw.trim();
        if (out.startsWith("/")) out = out.substring(1);
        return out;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private List<String> colorList(List<String> input) {
        List<String> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;
        for (String line : input) out.add(color(line));
        return out;
    }
}

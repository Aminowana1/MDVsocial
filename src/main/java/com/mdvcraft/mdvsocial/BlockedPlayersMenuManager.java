package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * "Jugadores Bloqueados" menu (Java inventory, Bedrock form through the
 * Bedrock module) and the /bloquear, /desbloquear and /bloqueados commands.
 */
public final class BlockedPlayersMenuManager implements Listener, CommandExecutor, TabCompleter {

    /** Menu id used by the "bloqueados" menu and as a return target of chat prompts. */
    public static final String MENU_ID = "bloqueados";

    private static final String CONFIG = "blocked-players.menu.";
    private static final int SIZE = 54;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34 };

    private final MDVSocialPlugin plugin;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyTarget;

    BlockedPlayersMenuManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "blocked_action");
        this.keyTarget = new NamespacedKey(plugin, "blocked_target");
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String name : List.of("bloquear", "desbloquear", "bloqueados")) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().warning("No se pudo registrar /" + name + " porque no existe en plugin.yml.");
                continue;
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    // ------------------------------------------------------------------ menu

    void open(Player player, int page) {
        if (plugin.isBedrockPlayer(player)) {
            plugin.bedrock().blocked().openList(player, page);
            return;
        }
        List<UUID> blocked = plugin.getBlockService().blockedPlayers(player.getUniqueId());
        int maxPage = Math.max(0, (blocked.size() - 1) / ENTRY_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        String count = String.valueOf(blocked.size());

        Holder holder = new Holder(safePage);
        Inventory inv = Bukkit.createInventory(holder, SIZE, plugin.color(text("title",
                "&8&lJugadores Bloqueados &7({count})").replace("{count}", count)));
        holder.inventory = inv;

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), "", null);
        for (int slot = 0; slot < SIZE; slot++)
            inv.setItem(slot, filler);

        int start = safePage * ENTRY_SLOTS.length;
        for (int i = 0; i < ENTRY_SLOTS.length && start + i < blocked.size(); i++)
            inv.setItem(ENTRY_SLOTS[i], entryItem(blocked.get(start + i)));
        if (blocked.isEmpty())
            inv.setItem(22, item(Material.PAPER, text("empty.name", "&7No tienes jugadores bloqueados"),
                    lines("empty.lore", List.of("", "&7Los jugadores bloqueados", "&7no pueden enviarte cartas.")),
                    "", null));

        if (safePage > 0)
            inv.setItem(45, item(Material.ARROW, text("previous", "&ePágina anterior"), List.of(), "PREVIOUS", null));
        inv.setItem(48, item(Material.OAK_DOOR, text("back", "&6&lVolver"),
                lines("back-lore", List.of("&7Regresa al menú de amigos.")), "BACK", null));
        inv.setItem(50, item(Material.BARRIER, text("block.name", "&c&lBloquear jugador"),
                lines("block.lore", List.of("", "&7Escribe el nombre del jugador", "&7que quieres bloquear.", "",
                        "&eClick para comenzar.")), "BLOCK", null));
        if (safePage < maxPage)
            inv.setItem(53, item(Material.ARROW, text("next", "&ePágina siguiente"), List.of(), "NEXT", null));

        player.openInventory(inv);
    }

    private ItemStack entryItem(UUID uuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
        String name = target.getName() == null ? uuid.toString().substring(0, 8) : target.getName();
        List<String> lore = new ArrayList<>();
        for (String line : lines("entry.lore", List.of("", "&7No puede enviarte cartas.", "",
                "&eClick para desbloquear.")))
            lore.add(line.replace("{target}", name));
        ItemStack item = item(Material.PLAYER_HEAD, text("entry.name", "&f{target}").replace("{target}", name),
                lore, "UNBLOCK", uuid);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
            item.setItemMeta(skull);
        }
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore, String action, UUID target) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        meta.setDisplayName(plugin.color(name));
        meta.setLore(lore.stream().map(plugin::color).toList());
        meta.addItemFlags(ItemFlag.values());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!action.isBlank())
            pdc.set(keyAction, PersistentDataType.STRING, action);
        if (target != null)
            pdc.set(keyTarget, PersistentDataType.STRING, target.toString());
        item.setItemMeta(meta);
        return item;
    }

    private String text(String path, String def) {
        return config().getString(CONFIG + path, def);
    }

    private List<String> lines(String path, List<String> def) {
        List<String> configured = config().getStringList(CONFIG + path);
        return configured.isEmpty() ? def : configured;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    /** True for inventories of this menu; other click handlers must ignore them. */
    static boolean isMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Holder;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder))
            return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory)
            return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;
        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.getOrDefault(keyAction, PersistentDataType.STRING, "");
        switch (action) {
            case "UNBLOCK" -> {
                String raw = pdc.get(keyTarget, PersistentDataType.STRING);
                try {
                    UUID target = UUID.fromString(raw);
                    plugin.playUiSound(player, "default");
                    plugin.unblockPlayer(player, target, Bukkit.getOfflinePlayer(target).getName());
                } catch (IllegalArgumentException | NullPointerException ignored) {
                }
                open(player, holder.page);
            }
            case "BLOCK" -> {
                plugin.playUiSound(player, "open");
                player.closeInventory();
                plugin.startBlockPlayerPrompt(player, true, MENU_ID, 1);
            }
            case "BACK" -> {
                plugin.playUiSound(player, "back");
                plugin.openCustomMenu(player, "menuamigos", 1, "main", 1, null, "", false);
            }
            case "PREVIOUS" -> open(player, holder.page - 1);
            case "NEXT" -> open(player, holder.page + 1);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    // -------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg(sender, "only-players");
            return true;
        }
        if (!player.hasPermission("mdvsocial.block.use")) {
            plugin.msg(player, "no-permission");
            return true;
        }
        String name = args.length >= 1 ? args[0] : "";
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "bloquear" -> {
                if (name.isBlank())
                    plugin.startBlockPlayerPrompt(player, true, MENU_ID, 1);
                else
                    plugin.blockPlayerByName(player, name);
            }
            case "desbloquear" -> {
                if (name.isBlank())
                    plugin.startBlockPlayerPrompt(player, false, MENU_ID, 1);
                else
                    plugin.unblockPlayerByName(player, name);
            }
            default -> open(player, 0);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1)
            return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("desbloquear")) {
            for (UUID uuid : plugin.getBlockService().blockedPlayers(player.getUniqueId())) {
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name != null)
                    names.add(name);
            }
        } else if (command.getName().equalsIgnoreCase("bloquear")) {
            for (Player online : Bukkit.getOnlinePlayers())
                if (!online.equals(player))
                    names.add(online.getName());
        }
        return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private static final class Holder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        Holder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
